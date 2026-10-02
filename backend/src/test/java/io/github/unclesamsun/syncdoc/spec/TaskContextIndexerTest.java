package io.github.unclesamsun.syncdoc.spec;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;
import io.github.unclesamsun.syncdoc.document.MarkdownRenderService.DocumentHeading;
class TaskContextIndexerTest {
    TraceabilityAnalyzer.Source source(String body){return new TraceabilityAnalyzer.Source("taskdoc","docs/task.md","---\nid: DOC-001\ntype: tasks\nstatus: 확정\n---\n\n"+body,List.of(new DocumentHeading(2,"actual-task","TASK-001 작업"),new DocumentHeading(2,"actual-next","TASK-002 다음")));}
    String block(String validation){return "## TASK-001 작업\n\n**목적:** 목적\n\n**근거:** 해당 없음\n\n**범위:** 범위\n\n**선행:** 해당 없음\n\n**산출물:** 파일\n\n**검증:** "+validation+"\n\n**완료:** 사람 확인\n";}
    TaskContextIndex index(String body){var sources=List.of(source(body));return new TaskContextIndexer().index(sources,new TraceabilityAnalyzer().analyze(sources),List.of(new TaskContextIndexer.RuleInput("AGENTS.md","abc")));}
    @Test void preserves_raw_commands_and_fences_with_actual_task_boundaries(){
        var report=index(block("`gradlew test`\n```sh\n# 참고 자료\n**범위:** 가짜 라벨\n```"));
        assertThat(report.indexStatus()).isEqualTo("complete");
        assertThat(report.tasks().getFirst().fields().get("검증")).contains("`gradlew test`","**범위:** 가짜 라벨");
        assertThat(report.tasks().getFirst().fields().get("범위")).isEqualTo("범위");
        assertThat(report.rules().getFirst().sourceHash()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
    @Test void html_labels_do_not_cut_commands_and_crlf_is_stable(){
        String text=block("실제 <span>**완료:** 가짜</span> 명령");
        assertThat(index(text).tasks().getFirst().fields().get("검증")).contains("명령");
        assertThat(index(text.replace("\n","\r\n")).tasks()).isEqualTo(index(text).tasks());
    }
    @Test void missing_duplicate_and_truncated_fields_are_not_complete(){
        assertThat(index(block("검증").replace("**완료:** 사람 확인","" )).indexStatus()).isEqualTo("partial");
        var duplicate=index(block("검증")+"\n**검증:** 두 번째 명령");
        assertThat(duplicate.indexStatus()).isEqualTo("partial");assertThat(duplicate.tasks().getFirst().fields()).doesNotContainKey("검증");
        var longField=index(block("x".repeat(5000)));assertThat(longField.tasks().getFirst().truncated()).isTrue();assertThat(longField.tasks().getFirst().fields().get("검증")).hasSize(4000);
    }
    @Test void does_not_merge_two_tasks_and_missing_rules_stay_unavailable(){
        var report=index(block("검증")+"\n## TASK-002 다음\n\n**목적:** 두 번째 목적");
        assertThat(report.tasks()).hasSize(2);assertThat(report.tasks().getFirst().fields().get("완료")).doesNotContain("TASK-002");
        var sources=List.of(source(block("검증")));var missing=new TaskContextIndexer().index(sources,new TraceabilityAnalyzer().analyze(sources),List.of(new TaskContextIndexer.RuleInput("AGENTS.md",null)));
        assertThat(missing.rules().getFirst().available()).isFalse();assertThat(missing.indexStatus()).isEqualTo("partial");
    }
}
