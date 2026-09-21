package io.github.unclesamsun.syncdoc.document;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.github.unclesamsun.syncdoc.document.domain.DocumentEntity;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

/**
 * 원문 Markdown을 읽을 수 있는 본문으로 바꾼다.
 *
 * <p>변환은 수집할 때 한 번만 한다. 조회할 때마다 다시 변환하지 않으며, 변환 규칙이 바뀌면
 * {@link DocumentVersions}의 버전을 올려 게시본을 다시 만든다.
 *
 * <p>다이어그램 원문은 HTML에 넣지 않는다. 본문에는 자리 표시만 남기고 원문은 별도 필드로 보낸다.
 * 브라우저가 그 원문을 제한된 Mermaid에만 넘기고 코드로 평가하지 않게 하기 위해서다.
 */
@Service
public class MarkdownRenderService {

    /** 변환기 자체의 버전. 규칙이 바뀌면 올린다. */
    public static final String VERSION = DocumentVersions.RENDERER;

    private final SpecMetadataParser metadata;
    private final HtmlPolicy policy;
    private final Parser parser;
    private final HtmlRenderer.Builder rendererBuilder;

    public MarkdownRenderService(SpecMetadataParser metadata, HtmlPolicy policy) {
        this.metadata = metadata;
        this.policy = policy;
        List<org.commonmark.Extension> extensions = List.of(
                TablesExtension.create(), YamlFrontMatterExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.rendererBuilder = HtmlRenderer.builder().extensions(extensions);
    }

    /** @param level 1~6, @param id 앵커, @param text 제목 글자 */
    public record DocumentHeading(int level, String id, String text) {
    }

    /** @param source 원문 그대로. 서버는 이 값을 실행하거나 해석하지 않는다 */
    public record DocumentDiagram(String id, String syntax, String source) {
    }

    /**
     * @param kind document·asset·external·anchor·missing
     * @param href 서비스 경로 또는 원래 주소. missing이면 이동할 곳이 없다
     */
    public record DocumentLink(String kind, String href, String text) {
    }

    public record DocumentWarning(String code, String detail) {
    }

    public record RenderedDocument(String title, String specId, String kind, String html, String plainText,
                                   List<DocumentHeading> headings, List<DocumentDiagram> diagrams,
                                   List<DocumentLink> links, List<DocumentWarning> warnings, String state) {
    }

    /** 같은 게시본 안에서 링크 목적지를 찾아 준다. 문서 밖의 주소는 해소하지 않는다. */
    public interface LinkTargets {

        /** @return 같은 게시본의 문서 id. 그 경로에 문서가 없으면 null */
        UUID documentIdFor(String repositoryPath);

        /** @return 같은 게시본의 첨부 id. 그 경로에 받아들인 첨부가 없으면 null */
        UUID assetIdFor(String repositoryPath);
    }

    /**
     * @param projectId    링크를 서비스 경로로 만들 때 쓴다
     * @param snapshotId   첨부 주소에 함께 담는다. 과거 게시본의 그림을 현재 것으로 섞지 않기 위해서다
     * @param documentPath 저장소 기준 경로. 상대 링크를 푸는 기준이다
     */
    public RenderedDocument render(UUID projectId, UUID snapshotId, String documentPath, String markdown,
                                   LinkTargets targets) {
        Node document = parser.parse(markdown);
        SpecMetadataParser.SpecMetadata spec = metadata.parse(document);

        Collector collector = new Collector(projectId, snapshotId, documentPath, targets);
        document.accept(collector);

        String html = policy.sanitize(rendererBuilder
                .attributeProviderFactory(context -> collector.headingIds())
                .build()
                .render(document));
        String plainText = Jsoup.parse(html).text();

        return new RenderedDocument(
                collector.title(documentPath),
                spec.specId(),
                spec.kind(),
                html,
                plainText,
                List.copyOf(collector.headings),
                List.copyOf(collector.diagrams),
                List.copyOf(collector.links),
                List.copyOf(collector.warnings),
                DocumentEntity.VALID);
    }

    /** 제목 id 부여, 링크 변환, 다이어그램 분리, 첨부 표시를 한 번의 순회에서 처리한다. */
    private static final class Collector extends AbstractVisitor {

        private final UUID projectId;
        private final UUID snapshotId;
        private final String documentPath;
        private final LinkTargets targets;
        private final HeadingSlugs slugs = new HeadingSlugs();
        private final Map<Node, String> ids = new IdentityHashMap<>();
        private final List<DocumentHeading> headings = new ArrayList<>();
        private final List<DocumentDiagram> diagrams = new ArrayList<>();
        private final List<DocumentLink> links = new ArrayList<>();
        private final List<DocumentWarning> warnings = new ArrayList<>();
        private String firstHeading;

        private Collector(UUID projectId, UUID snapshotId, String documentPath, LinkTargets targets) {
            this.projectId = projectId;
            this.snapshotId = snapshotId;
            this.documentPath = documentPath;
            this.targets = targets;
        }

        @Override
        public void visit(Heading heading) {
            String text = textOf(heading);
            String id = slugs.nextFor(text);
            ids.put(heading, id);
            headings.add(new DocumentHeading(heading.getLevel(), id, text));
            if (firstHeading == null && heading.getLevel() == 1) {
                firstHeading = text;
            }
            visitChildren(heading);
        }

        @Override
        public void visit(FencedCodeBlock block) {
            String info = block.getInfo() == null ? "" : block.getInfo().trim().toLowerCase(java.util.Locale.ROOT);
            if (!info.equals("mermaid") && !info.startsWith("mermaid ")) {
                visitChildren(block);
                return;
            }
            String id = "d" + (diagrams.size() + 1);
            diagrams.add(new DocumentDiagram(id, "mermaid", block.getLiteral()));
            // 본문에는 자리만 남기고 원문은 응답의 별도 필드로 나간다.
            HtmlBlock placeholder = new HtmlBlock();
            placeholder.setLiteral("<div class=\"diagram\" data-diagram-id=\"" + id + "\"></div>\n");
            block.insertBefore(placeholder);
            block.unlink();
        }

        @Override
        public void visit(Link link) {
            String destination = link.getDestination() == null ? "" : link.getDestination().trim();
            DocumentLink resolved = resolve(destination, textOf(link));
            links.add(resolved);
            link.setDestination(resolved.href());
            visitChildren(link);
        }

        @Override
        public void visit(Image image) {
            String alt = textOf(image);
            String destination = image.getDestination() == null ? "" : image.getDestination().trim();
            String repositoryPath = destination.matches("(?i)^[a-z][a-z0-9+.-]*:.*")
                    ? null : DocumentPaths.resolve(documentPath, destination);
            UUID assetId = repositoryPath == null ? null : targets.assetIdFor(repositoryPath);
            if (assetId == null) {
                // 받아들이지 않은 형식이거나 저장소에 없는 그림이다. 대체 글자만 남기고 무엇이 빠졌는지 알린다.
                links.add(new DocumentLink("asset", "", alt));
                warnings.add(new DocumentWarning("ASSET_NOT_AVAILABLE", destination));
                Text replacement = new Text(alt.isBlank() ? "[이미지]" : alt);
                image.insertBefore(replacement);
                image.unlink();
                return;
            }
            String href = "/api/v1/projects/" + projectId + "/assets/" + assetId
                    + "?snapshotId=" + snapshotId;
            links.add(new DocumentLink("asset", href, alt));
            image.setDestination(href);
            visitChildren(image);
        }

        private DocumentLink resolve(String destination, String text) {
            if (destination.isEmpty()) {
                return new DocumentLink("missing", "", text);
            }
            if (destination.startsWith("#")) {
                return new DocumentLink("anchor", destination, text);
            }
            if (destination.matches("(?i)^(https?|mailto):.*")) {
                return new DocumentLink("external", destination, text);
            }
            if (destination.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) {
                // http·mailto가 아닌 규약은 열지 않는다. `javascript:`가 여기서 걸린다.
                warnings.add(new DocumentWarning("LINK_SCHEME_NOT_ALLOWED", destination));
                return new DocumentLink("missing", "", text);
            }

            String anchor = "";
            String path = destination;
            int hash = destination.indexOf('#');
            if (hash >= 0) {
                anchor = destination.substring(hash);
                path = destination.substring(0, hash);
            }
            String repositoryPath = DocumentPaths.resolve(documentPath, path);
            if (repositoryPath == null) {
                // 문서 경로 밖으로 나가는 링크다. 저장소 밖을 가리킬 수 있어 열지 않는다.
                warnings.add(new DocumentWarning("LINK_OUTSIDE_REPOSITORY", destination));
                return new DocumentLink("missing", "", text);
            }
            UUID targetId = targets.documentIdFor(repositoryPath);
            if (targetId == null) {
                warnings.add(new DocumentWarning("LINK_TARGET_NOT_FOUND", destination));
                return new DocumentLink("missing", "", text);
            }
            return new DocumentLink("document",
                    "/projects/" + projectId + "/documents/" + targetId + anchor, text);
        }

        private AttributeProvider headingIds() {
            return (node, tagName, attributes) -> {
                String id = ids.get(node);
                if (id != null) {
                    attributes.put("id", id);
                }
            };
        }

        private String title(String path) {
            if (firstHeading != null && !firstHeading.isBlank()) {
                return firstHeading;
            }
            if (!headings.isEmpty()) {
                return headings.getFirst().text();
            }
            String name = path.substring(path.lastIndexOf('/') + 1);
            return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
        }

        private static String textOf(Node node) {
            StringBuilder out = new StringBuilder();
            node.accept(new AbstractVisitor() {
                @Override
                public void visit(Text text) {
                    out.append(text.getLiteral());
                }
            });
            return out.toString().trim();
        }
    }
}
