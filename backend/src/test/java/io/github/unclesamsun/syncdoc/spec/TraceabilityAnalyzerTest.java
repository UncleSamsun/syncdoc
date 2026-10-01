package io.github.unclesamsun.syncdoc.spec;

import static org.assertj.core.api.Assertions.assertThat;
import io.github.unclesamsun.syncdoc.document.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TraceabilityAnalyzerTest {
    final TraceabilityAnalyzer analyzer = new TraceabilityAnalyzer();
    final MarkdownRenderService renderer = new MarkdownRenderService(new SpecMetadataParser(), new HtmlPolicy());
    TraceabilityAnalyzer.Source source(String path, String kind, String status, String body) {
        String md = "---\nid: DOC-001\ntype: " + kind + "\nstatus: " + status + "\n---\n\n# 문서\n\n" + body;
        return new TraceabilityAnalyzer.Source(UUID.nameUUIDFromBytes(path.getBytes()).toString(), path, md,
            renderer.render(UUID.randomUUID(), UUID.randomUUID(), path, md, new MarkdownRenderService.LinkTargets() {
                public UUID documentIdFor(String p) { return null; }
                public UUID assetIdFor(String p) { return null; }
                public String sourceUrlFor(String p) { return null; }
            }).headings());
    }
    TraceabilityAnalyzer.Source req() { return source("docs/req.md", "prd-requirements", "확정", "## REQ-001 로그인\n\n## REQ-002 읽기\n"); }
    TraceabilityAnalyzer.Source task(String evidence) { return source("docs/task.md", "tasks", "확정", "## TASK-001 구현\n\n" + evidence); }
    SpecTraceability analyze(String evidence) { return analyzer.analyze(List.of(req(), task(evidence))); }
    @Test void valid_links_preserve_renderer_anchors_and_deduplicate() {
        var result = analyze("**근거:** [REQ-001](req.md)·REQ-001");
        assertThat(result.analysisStatus()).isEqualTo("complete");
        assertThat(result.edges()).hasSize(1);
        assertThat(result.requirements().getFirst().anchor()).isEqualTo(req().headings().get(1).id());
        assertThat(result.requirements().getFirst().line()).isEqualTo(9);
    }
    @Test void ranges_and_list_evidence_are_supported() {
        assertThat(analyze("**근거:** 다음 목록\n\n- REQ-001 ~ REQ-002").edges()).hasSize(2);
    }
    @Test void examples_and_code_are_excluded() {
        var result = analyzer.analyze(List.of(req(), task("**근거:** `REQ-001`\n\n```md\nREQ-002\n```"), source("guide.md", "guide", "확정", "## REQ-001 예시"), source("draft.md", "tasks", "검토", "## TASK-001 예시")));
        assertThat(result.analysisStatus()).isEqualTo("complete");
        assertThat(result.edges()).isEmpty();
        assertThat(result.findings()).extracting(SpecTraceability.Finding::code).contains("TASK_WITHOUT_REQUIREMENT");
    }
    @Test void duplicates_are_partial_and_do_not_choose_a_definition() {
        var result = analyzer.analyze(List.of(req(), source("docs/other.md", "prd-requirements", "확정", "## REQ-001 중복"), task("**근거:** REQ-001")));
        assertThat(result.analysisStatus()).isEqualTo("partial");
        assertThat(result.edges()).isEmpty();
        assertThat(result.findings()).extracting(SpecTraceability.Finding::code).contains("DUPLICATE_ITEM_ID");
    }
    @Test void missing_target_and_mismatched_path_are_known_errors() {
        var result = analyze("**근거:** REQ-099 [REQ-001](other.md)");
        assertThat(result.analysisStatus()).isEqualTo("complete");
        assertThat(result.edges()).isEmpty();
        assertThat(result.findings()).extracting(SpecTraceability.Finding::code).contains("MISSING_REQUIREMENT", "REFERENCE_TARGET_MISMATCH");
    }
    @Test void unsupported_ids_ranges_and_unreadable_evidence_are_partial() {
        for (String evidence : List.of("**근거:** REQ-ABC", "**근거:** REQ-000", "**근거:** REQ-1000", "**근거:** REQ-002 ~ REQ-001", "**근거:** REQ-001 ~ TASK-002", "**근거:**", "본문만 있음"))
            assertThat(analyze(evidence).analysisStatus()).as(evidence).isEqualTo("partial");
    }
    @Test void missing_sources_and_bad_status_are_distinct() {
        assertThat(analyzer.analyze(List.of(req())).uncheckedReason()).isEqualTo("NO_CONFIRMED_TASKS");
        assertThat(analyzer.analyze(List.of(task("**근거:** 없음"))).uncheckedReason()).isEqualTo("NO_CONFIRMED_REQUIREMENTS");
        assertThat(analyzer.analyze(List.of(req(), task("**근거:** REQ-001"), source("bad.md", "tasks", "invalid", "## TASK-002 오류"))).analysisStatus()).isEqualTo("partial");
    }
    @Test void output_is_deterministic_and_json_roundtrips() {
        var result = analyze("**근거:** REQ-002, REQ-001");
        assertThat(analyzer.analyze(List.of(task("**근거:** REQ-002, REQ-001"), req()))).isEqualTo(result);
        var json = new tools.jackson.databind.ObjectMapper();
        assertThat(json.readValue(json.writeValueAsString(result), SpecTraceability.class)).isEqualTo(result);
    }
    @Test void root_relative_links_external_links_and_html_are_handled_without_fetching() {
        var result = analyzer.analyze(List.of(source("req.md", "prd-requirements", "확정", "## REQ-001 요구"),
            source("task.md", "tasks", "확정", "## TASK-001 작업\n\n**근거:** [REQ-001](req.md) [REQ-001](https://github.com/owner/repo) <span>REQ-099</span>")));
        assertThat(result.edges()).hasSize(1);
        assertThat(result.findings()).isEmpty();
    }
    @Test void void_html_does_not_swallow_plain_references() {
        for (String tag : List.of("<br>", "<img src=\"x\">", "<BR>"))
            assertThat(analyze("**근거:** " + tag + " REQ-001").edges()).as(tag).hasSize(1);
    }
    @Test void malformed_ranges_do_not_contribute_either_endpoint() {
        for (String text : List.of("TASK-001 ~ REQ-002", "REQ-001 ~ REQ-ABC", "REQ-001 ~", "REQ-001 ~ TASK-002")) {
            var report = analyze("**근거:** " + text);
            assertThat(report.analysisStatus()).as(text).isEqualTo("partial");
            assertThat(report.edges()).as(text).isEmpty();
        }
        assertThat(analyze("**근거:** TASK-001 ~ TASK-002").analysisStatus()).isEqualTo("complete");
    }
    @Test void loose_list_diagnostics_and_edges_preserve_original_source_lines() {
        var input = task("**근거:** 아래 목록\n\n- REQ-001\n\n\n- REQ-099");
        var report = analyzer.analyze(List.of(req(), input));
        String[] lines = input.markdown().split("\n");
        int expected = java.util.stream.IntStream.range(0,lines.length).filter(n->lines[n].contains("REQ-099")).findFirst().orElseThrow()+1;
        assertThat(report.findings().stream().filter(f->f.code().equals("MISSING_REQUIREMENT")).findFirst().orElseThrow().line()).isEqualTo(expected);
    }
}
