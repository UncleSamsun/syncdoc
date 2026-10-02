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

@Import(ContextPersistenceTest.Gateways.class)
class ContextPersistenceTest extends PostgresContainerSupport {
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
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean RepositoryContentGateway ruleGateway;
 @Autowired ObjectMapper json;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean TaskContextIndexer indexer;
 @BeforeEach void reset() { fake().reset(); }
 FakeRepositoryContentGateway fake() { return (FakeRepositoryContentGateway) gateway; }
 static String md(String id, String type, String body) { return "---\nid: " + id + "\ntype: " + type + "\nstatus: 확정\n---\n\n" + body; }
 UUID collect(UUID id) { queue.request(id, true); worker.runOnce(); return projects.findById(id).orElseThrow().getCurrentSnapshotId(); }
 @Test void scope_changes_at_same_revision_do_not_reuse_old_documents() {
  var project=fixture.newProject("scope1");
  fake().putFile("rev-1","docs/req.md",md("DOC-001","prd-requirements","## REQ-001 요구\n본문"));
  fake().putFile("rev-1","other/req.md",md("DOC-002","prd-requirements","## REQ-002 다른요구\n본문"));
  UUID first=collect(project.getId());
  var before=snapshots.findById(first).orElseThrow();
  assertThat(before.getCollectionDocsRoot()).isEqualTo("docs");
  assertThat(before.getContextJson()).isNotNull();
  project=projects.findById(project.getId()).orElseThrow(); project.reconfigure("main","other",null);projects.saveAndFlush(project);fake().docsRoot("other");
  UUID second=collect(project.getId()); assertThat(second).isNotEqualTo(first);
  assertThat(snapshots.findById(second).orElseThrow().getCollectionDocsRoot()).isEqualTo("other");
  assertThat(snapshots.findById(first).orElseThrow().getContextJson()).isEqualTo(before.getContextJson());
  assertThat(collect(project.getId())).isEqualTo(second);
  project=projects.findById(project.getId()).orElseThrow();project.reconfigure("main","docs",null);projects.saveAndFlush(project);fake().docsRoot("docs");
  assertThat(collect(project.getId())).isEqualTo(first);
 }
 @Test void relation_failure_does_not_replace_previous_snapshot() {
  var project=fixture.newProject("scope2");fake().putFile("rev-1","docs/a.md","# A");UUID first=collect(project.getId());
  fake().head("rev-2");fake().putFile("rev-2","docs/a.md","# B");
  org.mockito.Mockito.doThrow(new IllegalStateException("index failed")).when(indexer).index(org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyList());
  assertThat(collect(project.getId())).isEqualTo(first);
  assertThat(status.statusOf(project.getId()).errorCode()).isEqualTo("COLLECTION_FAILED");
 }
 @Test void a_lease_lost_during_relation_analysis_cannot_publish() {
  var p=fixture.newProject("scope3");fake().putFile("rev-1","docs/a.md","# A");UUID before=collect(p.getId());
  fake().head("rev-2");fake().putFile("rev-2","docs/a.md","# B");
  org.mockito.Mockito.doAnswer(invocation->{Object report=invocation.callRealMethod();jdbc.update("update sync_jobs set lease_until=now()-interval '1 hour' where state='running'");assertThat(queue.claimNext()).isPresent();return report;}).when(indexer).index(org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyList());
  assertThat(collect(p.getId())).isEqualTo(before);
 }
 @Test void transient_rule_failure_preserves_previous_snapshot_and_retry_repairs_same_revision() {
  var p=fixture.newProject("rule-failure");fake().putFile("rev-1","docs/a.md","# A");UUID before=collect(p.getId());String original=snapshots.findById(before).orElseThrow().getContextJson();
  fake().head("rev-2");fake().putFile("rev-2","docs/a.md","# B");
  org.mockito.Mockito.doThrow(new GitHubLookupFailedException("rule unavailable")).when(ruleGateway).readRuleTextAt(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("rev-2"),org.mockito.ArgumentMatchers.eq("AGENTS.md"),org.mockito.ArgumentMatchers.anyInt());
  assertThat(collect(p.getId())).isEqualTo(before);assertThat(status.statusOf(p.getId()).errorCode()).isEqualTo("GITHUB_UNAVAILABLE");
  org.mockito.Mockito.doCallRealMethod().when(ruleGateway).readRuleTextAt(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("rev-2"),org.mockito.ArgumentMatchers.eq("AGENTS.md"),org.mockito.ArgumentMatchers.anyInt());fake().putFile("rev-2","AGENTS.md","restored rules");
  UUID after=collect(p.getId());assertThat(after).isNotEqualTo(before);assertThat(snapshots.findById(before).orElseThrow().getContextJson()).isEqualTo(original);
  assertThat(json.readTree(snapshots.findById(after).orElseThrow().getContextJson()).path("rules").get(0).path("available").asBoolean()).isTrue();
 }
}
