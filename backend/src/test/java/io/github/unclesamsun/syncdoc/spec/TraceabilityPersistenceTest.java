package io.github.unclesamsun.syncdoc.spec;
import static org.assertj.core.api.Assertions.assertThat;
import io.github.unclesamsun.syncdoc.document.domain.*;
import io.github.unclesamsun.syncdoc.github.*;
import io.github.unclesamsun.syncdoc.project.domain.*;
import io.github.unclesamsun.syncdoc.support.*;
import io.github.unclesamsun.syncdoc.sync.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Import(TraceabilityPersistenceTest.Gateways.class)
class TraceabilityPersistenceTest extends PostgresContainerSupport {
 @TestConfiguration static class Gateways {
  @Bean @Primary RepositoryContentGateway fakeContents() { return new FakeRepositoryContentGateway(); }
 }
 @Autowired SyncWorker worker;
 @Autowired SyncQueue queue;
 @Autowired SyncStatusReader status;
 @Autowired ProjectRepository projects;
 @Autowired DocumentSnapshotRepository snapshots;
 @Autowired io.github.unclesamsun.syncdoc.dashboard.domain.TaskRepository tasks;
 @Autowired ProjectFixture fixture;
 @Autowired RepositoryContentGateway gateway;
 @Autowired ObjectMapper json;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean TraceabilityAnalyzer analyzer;
 @BeforeEach void reset() { fake().reset(); }
 FakeRepositoryContentGateway fake() { return (FakeRepositoryContentGateway) gateway; }
 static String md(String id, String type, String body) { return "---\nid: " + id + "\ntype: " + type + "\nstatus: 확정\n---\n\n" + body; }
 UUID collect(UUID id) { queue.request(id, true); worker.runOnce(); return projects.findById(id).orElseThrow().getCurrentSnapshotId(); }
 @Test void collection_persists_report_and_duplicate_tasks_do_not_abort_or_choose() {
  var project = fixture.newProject("trace1");
  fake().putFile("rev-1", "docs/req.md", md("DOC-001", "prd-requirements", "## REQ-001 요구"));
  fake().putFile("rev-1", "docs/task.md", md("DOC-002", "tasks", "## TASK-001 작업\n\n**근거:** REQ-001"));
  UUID first = collect(project.getId());
  var report = json.readValue(snapshots.findById(first).orElseThrow().getTraceabilityJson(), SpecTraceability.class);
  assertThat(report.edges()).hasSize(1);
  fake().head("rev-2");
  fake().putFile("rev-2", "docs/req.md", md("DOC-001", "prd-requirements", "## REQ-001 요구"));
  fake().putFile("rev-2", "docs/task.md", md("DOC-002", "tasks", "## TASK-001 작업\n\n**근거:** REQ-001\n\n## TASK-001 중복\n\n**근거:** REQ-001"));
  UUID second = collect(project.getId());
  assertThat(second).isNotEqualTo(first);
  var duplicate = json.readValue(snapshots.findById(second).orElseThrow().getTraceabilityJson(), SpecTraceability.class);
  assertThat(duplicate.analysisStatus()).isEqualTo("partial");
  assertThat(duplicate.edges()).isEmpty();
  assertThat(tasks.findBySnapshotIdOrderByTaskSpecId(second)).isEmpty();
  assertThat(json.readValue(snapshots.findById(first).orElseThrow().getTraceabilityJson(), SpecTraceability.class)).isEqualTo(report);
 }
 @Test void analysis_failure_keeps_the_previous_published_snapshot() {
  var project = fixture.newProject("trace2");
  fake().putFile("rev-1", "docs/guide.md", "# 안내");
  UUID first = collect(project.getId());
  fake().head("rev-2"); fake().putFile("rev-2", "docs/guide.md", "# 바뀐 안내");
  org.mockito.Mockito.doThrow(new IllegalStateException("test analyzer failure")).when(analyzer).analyze(org.mockito.ArgumentMatchers.anyList());
  assertThat(collect(project.getId())).isEqualTo(first);
  assertThat(status.statusOf(project.getId()).errorCode()).isEqualTo("COLLECTION_FAILED");
 }
 @Test void a_lease_lost_during_analysis_cannot_publish() {
  var project = fixture.newProject("trace3");
  fake().putFile("rev-1", "docs/guide.md", "# 안내"); UUID first = collect(project.getId());
  fake().head("rev-2"); fake().putFile("rev-2", "docs/guide.md", "# 바뀐 안내");
  org.mockito.Mockito.doAnswer(invocation -> {
    Object result = invocation.callRealMethod();
    jdbc.update("update sync_jobs set lease_until = now() - interval '1 hour' where state = 'running'");
    assertThat(queue.claimNext()).isPresent();
    return result;
  }).when(analyzer).analyze(org.mockito.ArgumentMatchers.anyList());
  assertThat(collect(project.getId())).isEqualTo(first);
 }
 @Test void same_revision_with_old_policy_creates_a_new_analyzed_snapshot() {
  var project = fixture.newProject("trace4");
  var legacy = new DocumentSnapshotEntity(project.getId(), "rev-1", io.github.unclesamsun.syncdoc.document.DocumentVersions.RENDERER, "allowlist+3", java.time.Instant.now());
  legacy.markComplete(); snapshots.saveAndFlush(legacy);
  jdbc.update("update projects set current_snapshot_id = ? where id = ?", legacy.getId(), project.getId());
  fake().putFile("rev-1", "docs/guide.md", "# 안내");
  UUID next = collect(project.getId());
  assertThat(next).isNotEqualTo(legacy.getId());
  assertThat(snapshots.findById(next).orElseThrow().getTraceabilityJson()).isNotNull();
  assertThat(snapshots.findById(legacy.getId()).orElseThrow().getTraceabilityJson()).isNull();
 }
}
