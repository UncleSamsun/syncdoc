package io.github.unclesamsun.syncdoc.spec;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;
import io.github.unclesamsun.syncdoc.document.MarkdownRenderService.DocumentHeading;
class ComparisonIndexerTest {
 final ComparisonIndexer indexer=new ComparisonIndexer();
 TraceabilityAnalyzer.Source source(String body) { return new TraceabilityAnalyzer.Source("d1","docs/req.md","---\nid: DOC-001\ntype: prd-requirements\nstatus: 확정\n---\n# 제목\n"+body,List.of(new DocumentHeading(1,"top","제목"),new DocumentHeading(2,"real-anchor","REQ-001 요구"))); }
 ComparisonIndex index(String body) {return indexer.index(List.of(source(body)));}
 @Test void body_only_change_and_real_anchor_are_preserved() {
  var before=index("## REQ-001 요구\n\n본문 A\n");var after=index("## REQ-001 요구\n\n본문 B\n");
  assertThat(before.items().getFirst().sectionHash()).isNotEqualTo(after.items().getFirst().sectionHash());
  assertThat(before.items().getFirst().anchor()).isEqualTo("real-anchor");
 }
 @Test void line_endings_are_normalized_but_whitespace_is_a_change() {
  assertThat(index("## REQ-001 요구\n본문\n").items().getFirst().sectionHash()).isEqualTo(index("## REQ-001 요구\r\n본문\r\n").items().getFirst().sectionHash());
  assertThat(index("## REQ-001 요구\n본문 \n").items().getFirst().sectionHash()).isNotEqualTo(index("## REQ-001 요구\n본문\n").items().getFirst().sectionHash());
 }
 @Test void fenced_headings_do_not_end_the_section_and_h1_does() {
  var one=index("## REQ-001 요구\n```md\n## REQ-999 가짜\n```\n본문\n# 새문서\n끝");
  assertThat(one.items()).hasSize(1);
  assertThat(one.items().getFirst().sectionHash()).isEqualTo(index("## REQ-001 요구\n```md\n## REQ-999 가짜\n```\n본문\n").items().getFirst().sectionHash());
 }
 @Test void duplicates_and_invalid_status_are_partial_and_outside_sections_do_not_change_hash() {
  assertThat(index("## REQ-001 요구\nA\n## REQ-001 중복\nB").indexStatus()).isEqualTo("partial");
  var source=source("## REQ-001 요구\nA");
  assertThat(indexer.index(List.of(new TraceabilityAnalyzer.Source("d1",source.path(),source.markdown().replace("status: 확정","status: unknown"),source.headings()))).indexStatus()).isEqualTo("partial");
  assertThat(indexer.index(List.of(new TraceabilityAnalyzer.Source("d1",source.path(),source.markdown().replace("# 제목","# 다른 서론"),source.headings()))).items().getFirst().sectionHash()).isEqualTo(indexer.index(List.of(source)).items().getFirst().sectionHash());
 }
}
