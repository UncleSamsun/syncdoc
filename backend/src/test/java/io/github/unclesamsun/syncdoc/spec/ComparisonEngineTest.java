package io.github.unclesamsun.syncdoc.spec;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;
class ComparisonEngineTest {
 final ComparisonEngine engine=new ComparisonEngine();
 ComparisonEngine.Document doc(String uuid,String id,String path,String hash){return new ComparisonEngine.Document(uuid,id,path,"문서","prd-requirements",hash.repeat(64),"확정");}
 ComparisonIndex index(String status,ComparisonIndex.Item... items){return new ComparisonIndex(1,ComparisonIndex.ALGORITHM,status,List.of(),List.of(items),List.of());}
 ComparisonIndex.Item item(String kind,String id,String doc,String hash){return new ComparisonIndex.Item(kind,id,doc,"동일 제목",id.toLowerCase(),10,hash.repeat(64));}
 SpecTraceability trace(String status,String task,String req){return new SpecTraceability(1,status,null,List.of(),List.of(),task==null?List.of():List.of(new SpecTraceability.Edge(task,req,new SpecTraceability.Location("d","docs/a.md",10))),List.of());}
 @Test void body_change_with_same_title_finds_old_and_new_tasks() {
  var old=index("complete",item("req","REQ-001","d1","a"),item("task","TASK-001","d1","a"));
  var now=index("complete",item("req","REQ-001","d2","b"),item("task","TASK-002","d2","a"));
  var report=engine.compare(List.of(doc("d1","DOC-001","docs/a.md","a")),old,trace("complete","TASK-001","REQ-001"),List.of(doc("d2","DOC-001","docs/a.md","b")),now,trace("complete","TASK-002","REQ-001"));
  assertThat(report.items().stream().filter(r->r.key().equals("req:REQ-001")).findFirst().orElseThrow().change()).isEqualTo("modified");
  assertThat(report.impacts()).extracting(ComparisonEngine.Impact::taskId).containsExactly("TASK-001","TASK-002");
  assertThat(report.impacts().getFirst().taskPresence()).isEqualTo("removed");
 }
 @Test void doc_move_keeps_id_and_does_not_trigger_content_impact() {
  var report=engine.compare(List.of(doc("d1","DOC-001","docs/a.md","a")),index("complete",item("req","REQ-001","d1","a")),trace("complete",null,null),List.of(doc("d2","DOC-001","docs/new/a.md","a")),index("complete",item("req","REQ-001","d2","a")),trace("complete",null,null));
  assertThat(report.documents().getFirst().change()).isEqualTo("moved");assertThat(report.items().getFirst().change()).isEqualTo("moved");assertThat(report.impacts()).isEmpty();
 }
 @Test void id_replacement_is_not_matched_by_path_and_partial_missing_items_are_unknown() {
  var report=engine.compare(List.of(doc("d1","DOC-001","docs/a.md","a")),index("complete",item("req","REQ-001","d1","a")),trace("complete",null,null),List.of(doc("d2","DOC-002","docs/a.md","a")),index("partial"),trace("partial",null,null));
  assertThat(report.documents()).extracting(ComparisonEngine.Row::change).containsExactlyInAnyOrder("added","removed");
  assertThat(report.findings()).anyMatch(s->s.contains("identity_changed"));assertThat(report.items().getFirst().change()).isEqualTo("unknown");
 }
 @Test void duplicate_ids_and_algorithm_changes_never_claim_unchanged() {
  var left=index("complete",item("req","REQ-001","d1","a"));var right=index("partial",item("req","REQ-001","d2","a"),item("req","REQ-001","d2","a"));
  var report=engine.compare(List.of(doc("d1","DOC-001","docs/a.md","a")),left,trace("complete",null,null),List.of(doc("d2","DOC-001","docs/a.md","a")),right,trace("complete",null,null));
  assertThat(report.items().getFirst().change()).isEqualTo("unknown");
  var algorithm=new ComparisonIndex(1,"future","complete",List.of(),left.items(),List.of());
  assertThat(engine.compare(List.of(doc("d1","DOC-001","docs/a.md","a")),left,trace("complete",null,null),List.of(doc("d1","DOC-001","docs/a.md","a")),algorithm,trace("complete",null,null)).items().getFirst().change()).isEqualTo("unknown");
 }
 @Test void identical_valid_snapshots_and_unchecked_relations_are_not_mixed() {
  var i=index("complete",item("req","REQ-001","d1","a"));var d=List.of(doc("d1","DOC-001","docs/a.md","a"));
  var report=engine.compare(d,i,trace("unchecked",null,null),d,i,trace("complete",null,null));
  assertThat(report.items().getFirst().change()).isEqualTo("unchanged");assertThat(report.coverage()).isEqualTo("unknown");assertThat(report.impacts()).isEmpty();
 }
 @Test void moved_requirements_keep_current_reference_diagnostics() {
  var current=new SpecTraceability(1,"complete",null,List.of(),List.of(),List.of(),List.of(new SpecTraceability.Finding("REFERENCE_TARGET_MISMATCH","error","t","docs/tasks.md",10,"TASK-001","REQ-001","링크 경로 불일치")));
  var result=engine.compare(List.of(doc("d1","DOC-001","docs/a.md","a")),index("complete",item("req","REQ-001","d1","a")),trace("complete",null,null),List.of(doc("d2","DOC-001","docs/moved.md","a")),index("complete",item("req","REQ-001","d2","a")),current);
  assertThat(result.impacts()).isEmpty();assertThat(result.findings()).anyMatch(f->f.contains("to:")&&f.contains("REFERENCE_TARGET_MISMATCH"));
 }
}
