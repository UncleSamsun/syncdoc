package io.github.unclesamsun.syncdoc.spec;

import io.github.unclesamsun.syncdoc.document.MarkdownRenderService.DocumentHeading;
import io.github.unclesamsun.syncdoc.document.SpecMetadataParser;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

/** AST-only analysis of confirmed REQ definitions and TASK evidence. No network or database IO. */
@Component
public class TraceabilityAnalyzer {
    public record Source(String documentId, String path, String markdown, List<DocumentHeading> headings) {}
    private record Definition(SpecTraceability.Item item, List<Node> body) {}
    record LinkSpan(int start, int end, String target) {}
    record Evidence(String text, List<LinkSpan> links, int line) {}
    record LabelEvidence(String label, Evidence payload, Node source) {}
    private static final Pattern DEFINITION = Pattern.compile("^(REQ|TASK)-(\\d{3})(?:\\s+(.+))?$");
    private static final Pattern REFERENCES = Pattern.compile("(?<![A-Za-z0-9_-])(?:[A-Za-z]+-[A-Za-z0-9]+\\s*[~～]\\s*[^\\s,;·)\\]<>]*|REQ-[A-Za-z0-9]+)(?![A-Za-z0-9_-])");
    private final Parser parser = Parser.builder().extensions(List.of(YamlFrontMatterExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();
    private final SpecMetadataParser metadata = new SpecMetadataParser();

    public SpecTraceability analyze(List<Source> sources) {
        List<Definition> requirements = new ArrayList<>(), tasks = new ArrayList<>();
        List<SpecTraceability.Finding> findings = new ArrayList<>();
        boolean partial = false;
        for (Source source : sources.stream().sorted(Comparator.comparing(Source::path)).toList()) {
            Node root = parser.parse(source.markdown());
            var meta = metadata.parse(root);
            if (!Set.of("prd-requirements", "tasks").contains(Objects.toString(meta.kind(), ""))) continue;
            if (!Set.of("초안", "검토", "확정", "폐기").contains(Objects.toString(meta.status(), ""))) {
                findings.add(finding("INVALID_METADATA", "error", source, 1, null, null, "대상 문서의 작성 상태를 해석할 수 없습니다."));
                partial = true;
                continue;
            }
            if (!"확정".equals(meta.status())) continue;
            String prefix = "tasks".equals(meta.kind()) ? "TASK" : "REQ";
            int headingIndex = 0, definitionCount = 0;
            // Renderer traversal order, including nested headings, supplies actual anchors.
            Map<Node, String> anchors = new IdentityHashMap<>();
            List<Heading> allHeadings = new ArrayList<>();
            root.accept(new AbstractVisitor() { @Override public void visit(Heading h) { allHeadings.add(h); visitChildren(h); } });
            for (Heading h : allHeadings) {
                if (headingIndex < source.headings().size()) anchors.put(h, source.headings().get(headingIndex).id());
                headingIndex++;
            }
            for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
                if (!(node instanceof Heading heading) || heading.getLevel() != 2) continue;
                String text = visible(heading).text().trim();
                var match = DEFINITION.matcher(text);
                if (!match.matches() || !prefix.equals(match.group(1)) || "000".equals(match.group(2))) {
                    if (text.startsWith(prefix + "-")) {
                        findings.add(finding("INVALID_DEFINITION", "error", source, line(node), null, null, "항목 정의의 ID 형식을 해석할 수 없습니다."));
                        partial = true;
                    }
                    continue;
                }
                definitionCount++;
                var item = new SpecTraceability.Item(prefix + "-" + match.group(2),
                        Objects.toString(match.group(3), ""), source.documentId(), source.path(), anchors.get(node), line(node));
                List<Node> body = new ArrayList<>();
                for (Node next = node.getNext(); next != null; next = next.getNext()) {
                    if (next instanceof Heading h && h.getLevel() <= 2) break;
                    body.add(next);
                }
                (prefix.equals("REQ") ? requirements : tasks).add(new Definition(item, body));
            }
            if (definitionCount == 0) {
                findings.add(finding("INVALID_DEFINITION", "error", source, 1, null, null, "확정 문서에서 항목 정의를 찾을 수 없습니다."));
                partial = true;
            }
        }
        Map<String, List<Definition>> reqById = index(requirements), taskById = index(tasks);
        for (var definitions : List.of(reqById, taskById)) for (var entry : definitions.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            partial = true;
            for (Definition d : entry.getValue()) findings.add(finding("DUPLICATE_ITEM_ID", d.item(), d.item().line(), entry.getKey(), "항목 ID가 중복되어 정의를 선택하지 않습니다."));
        }
        Map<String, SpecTraceability.Edge> edges = new TreeMap<>();
        for (Definition task : tasks) {
            List<Node> evidenceNodes = new ArrayList<>();
            boolean evidenceLabel = false, hasPayload = false;
            for (Node node : task.body()) {
                List<Paragraph> paragraphs = new ArrayList<>();
                node.accept(new AbstractVisitor() { @Override public void visit(Paragraph p) { paragraphs.add(p); } });
                boolean found = false;
                for (Paragraph paragraph : paragraphs) {
                    if (paragraph.getFirstChild() instanceof StrongEmphasis strong && "근거:".equals(visible(strong).text().trim())) {
                        found = true;
                        evidenceLabel = true;
                        evidenceNodes.add(paragraph);
                        // Code-only evidence exists, but contributes no relationships.
                        hasPayload |= paragraph.getFirstChild().getNext() != null && !plainPayload(paragraph).isBlank();
                    }
                }
                if (!found && evidenceLabel && (node instanceof BulletList || node instanceof OrderedList)) {
                    evidenceNodes.addAll(paragraphs);
                    hasPayload = true;
                } else if (!found && !(node instanceof BulletList || node instanceof OrderedList)) {
                    evidenceLabel = false;
                }
            }
            if (evidenceNodes.isEmpty() || !hasPayload) {
                findings.add(finding("TASK_EVIDENCE_UNREADABLE", task.item(), task.item().line(), null, "작업 근거 라벨이 없거나 비어 있습니다."));
                partial = true;
                continue;
            }
            boolean hasReference = false;
            for (Node node : evidenceNodes) {
                Evidence evidence = visible(node);
                var matches = REFERENCES.matcher(evidence.text());
                while (matches.find()) {
                    String candidate = matches.group();
                    if (!candidate.contains("REQ-")) continue;
                    hasReference = true;
                    String target = evidence.links().stream().filter(l -> matches.start() >= l.start() && matches.start() < l.end()).map(LinkSpan::target).findFirst().orElse(null);
                    int referenceLine = evidence.line() + (int) evidence.text().substring(0, matches.start()).chars().filter(c -> c == '\n').count();
                    List<String> ids = expand(candidate);
                    if (ids.isEmpty()) {
                        partial = true;
                        findings.add(finding("UNSUPPORTED_REFERENCE", task.item(), referenceLine, candidate, "ID 또는 범위 표기를 해석할 수 없습니다."));
                        continue;
                    }
                    for (String id : ids) {
                        var definitions = reqById.getOrDefault(id, List.of());
                        if (definitions.isEmpty()) {
                            findings.add(finding("MISSING_REQUIREMENT", task.item(), referenceLine, id, "참조한 요구 정의가 없습니다."));
                            continue;
                        }
                        if (definitions.size() != 1 || taskById.get(task.item().itemId()).size() != 1) continue;
                        var requirement = definitions.getFirst().item();
                        if (target != null && !targetPath(task.item().path(), target).map(requirement.path()::equals).orElse(true)) {
                            findings.add(finding("REFERENCE_TARGET_MISMATCH", task.item(), referenceLine, id, "링크 경로와 요구 정의가 일치하지 않습니다."));
                            continue;
                        }
                        edges.putIfAbsent(task.item().itemId() + ":" + id, new SpecTraceability.Edge(task.item().itemId(), id,
                                new SpecTraceability.Location(task.item().documentId(), task.item().path(), referenceLine)));
                    }
                }
            }
            if (!hasReference) findings.add(new SpecTraceability.Finding("TASK_WITHOUT_REQUIREMENT", "info", task.item().documentId(), task.item().path(), task.item().line(), task.item().itemId(), null, "근거에 요구 ID가 없습니다. 요구 연결이 필요 없는 작업일 수 있습니다."));
        }
        String reason = requirements.isEmpty() ? "NO_CONFIRMED_REQUIREMENTS" : tasks.isEmpty() ? "NO_CONFIRMED_TASKS" : null;
        // Malformed relevant inputs must stay partial even if no valid definitions survived.
        String status = partial ? "partial" : reason != null ? "unchecked" : "complete";
        return new SpecTraceability(1, status, status.equals("unchecked") ? reason : null,
                items(requirements), items(tasks), List.copyOf(edges.values()), findings.stream().distinct().sorted(
                    Comparator.comparing(SpecTraceability.Finding::path).thenComparingInt(SpecTraceability.Finding::line)
                    .thenComparing(SpecTraceability.Finding::code).thenComparing(f -> Objects.toString(f.targetId(), ""))).toList());
    }

    private static List<String> expand(String candidate) {
        String[] ids = candidate.split("\\s*[~～]\\s*", -1);
        if (Arrays.stream(ids).anyMatch(id -> !id.matches("REQ-(?!000)\\d{3}"))) return List.of();
        if (ids.length == 1) return List.of(ids[0]);
        int first = Integer.parseInt(ids[0].substring(4)), last = Integer.parseInt(ids[1].substring(4));
        if (first > last) return List.of();
        return java.util.stream.IntStream.rangeClosed(first, last).mapToObj(i -> "REQ-%03d".formatted(i)).toList();
    }
    static Optional<String> targetPath(String source, String target) {
        if (target.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") || target.startsWith("//")) return Optional.empty();
        try {
            String path = URLDecoder.decode(target.split("[?#]", 2)[0].replace("+", "%2B"), StandardCharsets.UTF_8);
            if (path.isEmpty()) return Optional.of(source);
            if (path.startsWith("/") || path.contains("\\")) return Optional.of("<invalid>");
            return Optional.of(Objects.requireNonNullElse(Path.of(source).getParent(), Path.of("" )).resolve(path).normalize().toString().replace('\\', '/'));
        } catch (RuntimeException e) { return Optional.of("<invalid>"); }
    }
    static int line(Node node) { return node.getSourceSpans().isEmpty() ? 1 : node.getSourceSpans().getFirst().getLineIndex() + 1; }
    private static String plainPayload(Paragraph paragraph) {
        StringBuilder text = new StringBuilder();
        for (Node n = paragraph.getFirstChild().getNext(); n != null; n = n.getNext()) {
            if (n instanceof Code code) text.append(code.getLiteral()); else text.append(visible(n).text());
        }
        return text.toString();
    }
    static Evidence visible(Node node) {
        StringBuilder text = new StringBuilder(); List<LinkSpan> links = new ArrayList<>();
        append(node, text, links, new int[]{0});
        return new Evidence(text.toString(), links, line(node));
    }
    static Evidence between(Node first, Node stop, int sourceLine) {
        StringBuilder text = new StringBuilder(); List<LinkSpan> links = new ArrayList<>();
        int[] htmlDepth = {0};
        for (Node child = first; child != null && child != stop; child = child.getNext()) append(child, text, links, htmlDepth);
        return new Evidence(text.toString(), links, sourceLine);
    }
    /** Field discovery and payload extraction share visibility state, including across inline HTML. */
    static List<LabelEvidence> labels(Paragraph paragraph) {
        List<LabelEvidence> result = new ArrayList<>();
        String label = null; Node labelNode = null; StringBuilder text = new StringBuilder(); List<LinkSpan> links = new ArrayList<>();
        int[] htmlDepth = {0};
        for (Node child = paragraph.getFirstChild(); child != null; child = child.getNext()) {
            String candidate = child instanceof StrongEmphasis && htmlDepth[0] == 0 ? visible(child).text().trim() : "";
            if (candidate.endsWith(":")) {
                if (label != null) result.add(new LabelEvidence(label, new Evidence(text.toString(), List.copyOf(links), line(paragraph)), labelNode));
                label = candidate; labelNode = child; text.setLength(0); links.clear();
            } else append(child, text, links, htmlDepth);
        }
        if (label != null) result.add(new LabelEvidence(label, new Evidence(text.toString(), List.copyOf(links), line(paragraph)), labelNode));
        return result;
    }
    private static void append(Node node, StringBuilder text, List<LinkSpan> links, int[] htmlDepth) {
        if (node instanceof Code || node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock || node instanceof HtmlBlock) { text.append(' '); return; }
        if (node instanceof HtmlInline html) {
            String literal = html.getLiteral();
            if (literal.matches("</[A-Za-z].*")) htmlDepth[0] = Math.max(0, htmlDepth[0] - 1);
            else if (literal.matches("<[A-Za-z].*") && !literal.endsWith("/>")
                    && !literal.matches("(?is)<(?:area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)(?:\\s.*|>)")) htmlDepth[0]++;
            text.append(' '); return;
        }
        if (node instanceof Text t) { if (htmlDepth[0] == 0) text.append(t.getLiteral()); return; }
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) { text.append('\n'); return; }
        int start = text.length();
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) append(child, text, links, htmlDepth);
        if (node instanceof Link link) links.add(new LinkSpan(start, text.length(), link.getDestination()));
        if (node instanceof Paragraph) text.append('\n');
    }
    private static Map<String,List<Definition>> index(List<Definition> definitions) {
        Map<String,List<Definition>> result = new TreeMap<>();
        definitions.forEach(d -> result.computeIfAbsent(d.item().itemId(), k -> new ArrayList<>()).add(d));
        return result;
    }
    private static List<SpecTraceability.Item> items(List<Definition> defs) {
        return defs.stream().map(Definition::item).sorted(Comparator.comparing(SpecTraceability.Item::itemId).thenComparing(SpecTraceability.Item::path).thenComparingInt(SpecTraceability.Item::line)).toList();
    }
    private static SpecTraceability.Finding finding(String code, String severity, Source s, int line, String id, String target, String message) {
        return new SpecTraceability.Finding(code, severity, s.documentId(), s.path(), line, id, target, message);
    }
    private static SpecTraceability.Finding finding(String code, SpecTraceability.Item item, int line, String target, String message) {
        return new SpecTraceability.Finding(code, "error", item.documentId(), item.path(), line, item.itemId(), target, message);
    }
}
