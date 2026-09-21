package io.github.unclesamsun.syncdoc.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.document.MarkdownRenderService.DocumentLink;
import io.github.unclesamsun.syncdoc.document.MarkdownRenderService.DocumentWarning;
import io.github.unclesamsun.syncdoc.document.MarkdownRenderService.RenderedDocument;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 변환과 정화 규칙. 문서를 쓴 사람의 원문이 읽는 사람의 브라우저에서 그대로 실행되지 않는 것과,
 * 이미 저장소에 있는 문서의 표·앵커·상대 링크가 그대로 읽히는 것이 검사 대상이다.
 */
class DocumentRenderingTest {

    private static final UUID PROJECT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GUIDE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final MarkdownRenderService renderer =
            new MarkdownRenderService(new SpecMetadataParser(), new HtmlPolicy());
    private final Map<String, UUID> snapshot = new HashMap<>(Map.of("docs/01-prd/guide.md", GUIDE));

    private RenderedDocument render(String markdown) {
        return render("docs/03-tech-spec/api-spec.md", markdown);
    }

    private RenderedDocument render(String path, String markdown) {
        return renderer.render(PROJECT, path, markdown, snapshot::get);
    }

    @Test
    void a_gfm_table_becomes_a_table() {
        RenderedDocument rendered = render("""
                | ID | 내용 |
                |---|---|
                | API-018 | 문서 본문 |
                """);

        assertThat(rendered.html()).contains("<table>").contains("<th>ID</th>").contains("<td>API-018</td>");
    }

    @Test
    void a_korean_heading_gets_the_same_anchor_github_would_give_it() {
        // 이 저장소의 문서가 이미 이 형태의 링크로 서로를 가리킨다.
        RenderedDocument rendered = render("## API-018 문서 본문\n\n내용");

        assertThat(rendered.headings()).singleElement()
                .satisfies(heading -> assertThat(heading.id()).isEqualTo("api-018-문서-본문"));
        assertThat(rendered.html()).contains("id=\"api-018-문서-본문\"");
    }

    @Test
    void repeated_headings_get_distinct_anchors() {
        RenderedDocument rendered = render("## 검증\n\n하나\n\n## 검증\n\n둘");

        assertThat(rendered.headings()).extracting(MarkdownRenderService.DocumentHeading::id)
                .containsExactly("검증", "검증-1");
    }

    @Test
    void an_explicit_anchor_link_is_left_alone() {
        RenderedDocument rendered = render("[API-018](#api-018-문서-본문)을 본다");

        assertThat(rendered.links()).singleElement()
                .satisfies(link -> {
                    assertThat(link.kind()).isEqualTo("anchor");
                    assertThat(link.href()).isEqualTo("#api-018-문서-본문");
                });
    }

    @Test
    void a_relative_link_to_another_document_becomes_a_service_path() {
        RenderedDocument rendered = render("[안내서](../01-prd/guide.md#적용-범위)를 본다");

        assertThat(rendered.links()).singleElement().satisfies(link -> {
            assertThat(link.kind()).isEqualTo("document");
            assertThat(link.href()).isEqualTo("/projects/" + PROJECT + "/documents/" + GUIDE + "#적용-범위");
        });
        assertThat(rendered.html()).contains("/documents/" + GUIDE + "#적용-범위");
    }

    @Test
    void a_link_to_a_document_that_is_not_in_this_snapshot_goes_nowhere() {
        RenderedDocument rendered = render("[없는 문서](./missing.md)");

        assertThat(rendered.links()).singleElement()
                .satisfies(link -> assertThat(link.kind()).isEqualTo("missing"));
        assertThat(rendered.warnings()).extracting(DocumentWarning::code)
                .contains("LINK_TARGET_NOT_FOUND");
        assertThat(rendered.html()).doesNotContain("missing.md");
    }

    @Test
    void a_link_that_climbs_out_of_the_repository_is_refused() {
        RenderedDocument rendered = render("docs/a.md", "[비밀](../../../../etc/passwd)");

        assertThat(rendered.links()).singleElement()
                .satisfies(link -> assertThat(link.href()).isEmpty());
        assertThat(rendered.warnings()).extracting(DocumentWarning::code)
                .contains("LINK_OUTSIDE_REPOSITORY");
    }

    @Test
    void a_javascript_link_never_reaches_the_page() {
        RenderedDocument rendered = render("[누르기](javascript:alert(1))");

        assertThat(rendered.html()).doesNotContain("javascript:");
        assertThat(rendered.warnings()).extracting(DocumentWarning::code)
                .contains("LINK_SCHEME_NOT_ALLOWED");
    }

    @Test
    void a_script_in_the_source_does_not_survive() {
        RenderedDocument rendered = render("""
                # 제목

                <script>alert('x')</script>

                <img src=x onerror="alert(1)">

                <div onclick="alert(2)">누르지 마세요</div>
                """);

        assertThat(rendered.html())
                .doesNotContain("<script")
                .doesNotContain("onerror")
                .doesNotContain("onclick")
                .doesNotContain("alert");
    }

    @Test
    void an_allowed_details_block_keeps_working() {
        RenderedDocument rendered = render("""
                <details>
                <summary>접힌 내용</summary>

                안에 든 글

                </details>
                """);

        assertThat(rendered.html()).contains("<details>").contains("<summary>접힌 내용</summary>");
    }

    @Test
    void a_mermaid_block_leaves_the_body_and_comes_back_as_source() {
        RenderedDocument rendered = render("""
                # 흐름

                ```mermaid
                flowchart TD
                  A --> B
                ```

                ```java
                int x = 1;
                ```
                """);

        assertThat(rendered.diagrams()).singleElement().satisfies(diagram -> {
            assertThat(diagram.syntax()).isEqualTo("mermaid");
            assertThat(diagram.source()).contains("flowchart TD");
        });
        // 본문에는 자리만 남는다. 원문이 HTML 안에서 다시 해석될 일이 없다.
        assertThat(rendered.html()).contains("data-diagram-id=\"d1\"").doesNotContain("flowchart TD");
        // 일반 코드 블록은 그대로 둔다.
        assertThat(rendered.html()).contains("int x = 1;");
    }

    @Test
    void frontmatter_becomes_metadata_and_not_body_text() {
        RenderedDocument rendered = render("""
                ---
                id: DOC-011
                type: tech-data
                status: 확정
                ---

                # MVP 데이터 모델

                본문
                """);

        assertThat(rendered.specId()).isEqualTo("DOC-011");
        assertThat(rendered.kind()).isEqualTo("tech-data");
        assertThat(rendered.title()).isEqualTo("MVP 데이터 모델");
        assertThat(rendered.html()).doesNotContain("DOC-011").doesNotContain("tech-data");
    }

    @Test
    void an_image_is_reported_as_a_missing_attachment_instead_of_a_broken_picture() {
        RenderedDocument rendered = render("![구성도](./images/architecture.png)");

        assertThat(rendered.html()).doesNotContain("<img").contains("구성도");
        assertThat(rendered.links()).extracting(DocumentLink::kind).contains("asset");
        assertThat(rendered.warnings()).extracting(DocumentWarning::code).contains("ASSET_NOT_AVAILABLE");
    }

    @Test
    void the_plain_text_has_no_markup_so_search_can_use_it() {
        RenderedDocument rendered = render("# 제목\n\n**굵은** 글과 `코드`");

        assertThat(rendered.plainText()).contains("제목").contains("굵은").doesNotContain("<");
    }

    @Test
    void a_document_without_a_heading_falls_back_to_its_file_name() {
        assertThat(render("docs/nested/plan.md", "본문만 있다").title()).isEqualTo("plan");
    }
}
