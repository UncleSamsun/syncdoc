package io.github.unclesamsun.syncdoc.spec;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;
class DesignImpactCalculatorTest {
    SpecRelations graph(String doc, String status) {
        var api=new SpecRelations.Item("api","API-001","API","a"+doc,"api.md","actual-api",8);
        var ui=new SpecRelations.Item("ui","UI-001","UI","u"+doc,"ui.md","actual-ui",10);
        return new SpecRelations(1,status,null,List.of(api,ui),List.of(
                new SpecRelations.Edge("api","API-001","req","REQ-001","requires",new SpecTraceability.Location("a"+doc,"api.md",8)),
                new SpecRelations.Edge("ui","UI-001","api","API-001","uses",new SpecTraceability.Location("u"+doc,"ui.md",10))),List.of());
    }
    @Test void changed_requirement_uses_both_sides_and_reverse_ui_api_paths() {
        var calculator=new DesignImpactCalculator();
        var row=new ComparisonEngine.Row("req:REQ-001","req","modified",null,null,null);
        var result=calculator.compute(List.of(row),graph("old","complete"),graph("new","complete"));
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(i->{assertThat(i.before().documentId()).contains("old");assertThat(i.after().documentId()).contains("new");});
        assertThat(calculator.compute(List.of(new ComparisonEngine.Row("req:REQ-001","req","moved",null,null,null)),graph("o","complete"),graph("n","complete"))).isEmpty();
    }
    @Test void missing_design_in_partial_target_is_unknown_and_removed_keeps_old_source() {
        var row=new ComparisonEngine.Row("req:REQ-001","req","removed",null,null,null);
        var absent=new SpecRelations(1,"partial",null,List.of(),List.of(),List.of());
        assertThat(new DesignImpactCalculator().compute(List.of(row),graph("old","complete"),absent)).allSatisfy(i->assertThat(i.presence()).isEqualTo("unknown"));
        var complete=new SpecRelations(1,"complete",null,List.of(),List.of(),List.of());
        assertThat(new DesignImpactCalculator().compute(List.of(row),graph("old","complete"),complete)).allSatisfy(i->{assertThat(i.presence()).isEqualTo("removed");assertThat(i.before()).isNotNull();assertThat(i.after()).isNull();});
    }
}
