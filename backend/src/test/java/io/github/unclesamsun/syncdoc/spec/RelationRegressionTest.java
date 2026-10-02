package io.github.unclesamsun.syncdoc.spec;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import io.github.unclesamsun.syncdoc.document.MarkdownRenderService.DocumentHeading;
class RelationRegressionTest {
    TraceabilityAnalyzer.Source raw(String path,String markdown) {
        var root=Parser.builder().extensions(List.of(YamlFrontMatterExtension.create(),TablesExtension.create())).build().parse(markdown);
        var headings=new ArrayList<DocumentHeading>();root.accept(new AbstractVisitor(){@Override public void visit(Heading h){headings.add(new DocumentHeading(h.getLevel(),"actual-"+headings.size(),TraceabilityAnalyzer.visible(h).text().trim()));visitChildren(h);}});
        return new TraceabilityAnalyzer.Source(path,path,markdown,headings);
    }
    TraceabilityAnalyzer.Source src(String path,String type,String body){return raw(path,"---\nid: DOC-001\ntype: "+type+"\nstatus: 확정\n---\n\n"+body);}
    SpecRelations run(TraceabilityAnalyzer.Source... sources){var input=List.of(sources);return new RelationAnalyzer().analyze(input,new TraceabilityAnalyzer().analyze(input));}
    TraceabilityAnalyzer.Source req(){return src("req.md","prd-requirements","## REQ-001 하나\n본문\n\n## REQ-002 둘\n본문");}
    @Test void ordinary_emphasis_is_payload_and_not_a_label_boundary(){
        var report=run(req(),src("ui.md","ui-screens","## UI-001 화면\n\n**연결 요구:** **REQ-001** 추가 **설명** REQ-002"));
        assertThat(report.edges()).hasSize(2);assertThat(report.analysisStatus()).isEqualTo("complete");
    }
    @Test void html_hidden_label_does_not_reset_payload_visibility(){
        var report=run(req(),src("ui.md","ui-screens","## UI-001 화면\n\n**연결 요구:** REQ-001 <span>example **연결 요구:** REQ-002</span>"));
        assertThat(report.edges()).hasSize(1);assertThat(report.edges().getFirst().targetId()).isEqualTo("REQ-001");
    }
    @Test void sentence_period_ends_valid_dependency_range(){
        var report=run(src("task.md","tasks","## TASK-001 하나\n\n**근거:** 해당 없음\n\n## TASK-002 둘\n\n**근거:** 해당 없음\n\n## TASK-003 셋\n\n**근거:** 해당 없음\n\n**선행:** TASK-001 ~ TASK-002."));
        assertThat(report.edges().stream().filter(e->e.relation().equals("depends_on"))).hasSize(2);
        assertThat(report.findings()).noneMatch(f->f.code().equals("UNSUPPORTED_REFERENCE"));
    }
    @Test void canonical_api_rows_are_actual_ast_definitions() throws Exception {
        var inputs=new ArrayList<TraceabilityAnalyzer.Source>();
        for(String path:List.of("01-prd/mvp-scope.md","02-ui-spec/ui-screens.md","03-tech-spec/api-spec.md","04-tasks/implementation-plan.md")) inputs.add(raw("docs/"+path,Files.readString(Path.of("../docs",path))));
        var report=new RelationAnalyzer().analyze(inputs,new TraceabilityAnalyzer().analyze(inputs));
        var api=inputs.get(2).markdown();var expected=api.lines().filter(line->line.matches("^\\| API-\\d{3} .*" )).map(line->line.substring(2,9)).toList();
        assertThat(report.nodes().stream().filter(n->n.kind().equals("api")).map(SpecRelations.Item::itemId)).containsAll(expected);
    }
}
