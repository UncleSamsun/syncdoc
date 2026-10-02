package io.github.unclesamsun.syncdoc.spec;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;
import io.github.unclesamsun.syncdoc.document.MarkdownRenderService.DocumentHeading;

class RelationAnalyzerTest {
    private TraceabilityAnalyzer.Source source(String id,String path,String type,String body,String... headings) {
        var items=new ArrayList<DocumentHeading>();
        for(int i=0;i<headings.length;i++) items.add(new DocumentHeading(2,"actual-"+i,headings[i]));
        return new TraceabilityAnalyzer.Source(id,path,"---\nid: DOC-001\ntype: "+type+"\nstatus: 확정\n---\n\n"+body,items);
    }
    private SpecRelations analyze(TraceabilityAnalyzer.Source... sources) {
        var input=List.of(sources);return new RelationAnalyzer().analyze(input,new TraceabilityAnalyzer().analyze(input));
    }
    @Test void ui_api_task_relations_use_labels_tables_and_actual_anchors() {
        var req=source("r","req.md","prd-requirements","## REQ-001 읽기\n설명","REQ-001 읽기");
        var api=source("a","api.md","tech-interface","## 계약 일람\n\n| ID | 메서드·경로 | 연결 요구 |\n|---|---|---|\n| API-001 | GET /a | REQ-001 |\n\n## API-001 설명\n설명","계약 일람","API-001 설명");
        var ui=source("u","ui.md","ui-screens","## 화면과 계약\n\n| 화면 | 부르는 계약 |\n|---|---|\n| UI-000 공통 셸 | API-001 |\n\n## UI-000 공통 셸\n\n**연결 요구:** REQ-001 · **관련 작업:** TASK-001","화면과 계약","UI-000 공통 셸");
        var task=source("t","task.md","tasks","## TASK-001 구현\n\n**근거:** REQ-001, UI-000, API-001","TASK-001 구현");
        var report=analyze(req,api,ui,task);
        assertThat(report.analysisStatus()).isEqualTo("complete");
        assertThat(report.nodes()).hasSize(4);
        assertThat(report.nodes().stream().filter(n->n.kind().equals("api")).findFirst().orElseThrow().anchor()).isEqualTo("actual-1");
        assertThat(report.nodes().stream().filter(n->n.kind().equals("api")).findFirst().orElseThrow().line()).isEqualTo(11);
        assertThat(report.edges()).hasSize(7);
        assertThat(report.edges()).anyMatch(e->e.sourceId().equals("UI-000")&&e.targetId().equals("TASK-001"));
    }
    @Test void missing_duplicate_wrong_paths_and_invalid_ranges_do_not_create_edges() {
        var r=source("r","req.md","prd-requirements","## REQ-001 읽기\nx","REQ-001 읽기");
        var u=source("u","ui.md","ui-screens","## UI-001 화면\n\n**연결 요구:** [REQ-001](wrong.md), REQ-999, REQ-001 ~ REQ-XYZ","UI-001 화면");
        var report=analyze(r,u);
        assertThat(report.edges()).isEmpty();
        assertThat(report.analysisStatus()).isEqualTo("partial");
        assertThat(report.findings()).extracting(SpecTraceability.Finding::code).contains("REFERENCE_TARGET_MISMATCH","MISSING_REFERENCE","UNSUPPORTED_REFERENCE");
        var duplicate=analyze(r,u,source("v","v.md","ui-screens","## UI-001 두 번째\n\n**연결 요구:** REQ-001","UI-001 두 번째"));
        assertThat(duplicate.edges()).isEmpty();
        assertThat(duplicate.findings()).extracting(SpecTraceability.Finding::code).contains("DUPLICATE_ITEM_ID");
    }
    @Test void code_html_drafts_and_later_labels_are_not_requirement_evidence() {
        var r=source("r","req.md","prd-requirements","## REQ-001 읽기\nx","REQ-001 읽기");
        var u=source("u","ui.md","ui-screens","## UI-001 화면\n\n**연결 요구:** `REQ-999` <span>REQ-888</span> REQ-001 · **관련 작업:** TASK-999\n\n```md\n## UI-999 가짜\n**연결 요구:** REQ-777\n```","UI-001 화면");
        var report=analyze(r,u);
        assertThat(report.nodes()).hasSize(2);
        assertThat(report.edges()).hasSize(1);
        assertThat(report.findings()).extracting(SpecTraceability.Finding::targetId).doesNotContain("REQ-999","REQ-888","REQ-777").contains("TASK-999");
        var draft=new TraceabilityAnalyzer.Source("d","d.md",u.markdown().replace("status: 확정","status: 검토"),u.headings());
        assertThat(analyze(r,draft).nodes()).hasSize(1);
    }
    @Test void dependency_cycles_are_diagnosed_without_rewriting_task_execution() {
        var t=source("t","task.md","tasks","## TASK-001 첫 작업\n\n**근거:** 요구 연결 없음\n\n**선행:** TASK-002\n\n## TASK-002 다음\n\n**근거:** 요구 연결 없음\n\n**선행:** TASK-001","TASK-001 첫 작업","TASK-002 다음");
        assertThat(analyze(t).findings()).extracting(SpecTraceability.Finding::code).contains("DEPENDENCY_CYCLE");
    }
}
