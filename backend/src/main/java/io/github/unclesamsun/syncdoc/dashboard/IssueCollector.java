package io.github.unclesamsun.syncdoc.dashboard;

import io.github.unclesamsun.syncdoc.dashboard.domain.IssueSnapshotEntity;
import io.github.unclesamsun.syncdoc.dashboard.domain.IssueSnapshotRepository;
import io.github.unclesamsun.syncdoc.github.RepositoryContentGateway;
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * GitHub의 Issue와 PR을 읽어 둔다.
 *
 * <p>문서 게시본과 달리 프로젝트 단위다. Issue 상태는 게시본과 무관하게 계속 바뀌므로, 지난 게시본을
 * 보는 중에도 Issue는 지금 상태를 보여준다.
 *
 * <p>읽지 못해도 수집 전체를 실패로 만들지 않는다. 권한이 아직 없을 수 있고, 그때는 문서 현황까지
 * 못 보게 되는 편이 더 나쁘다. 대신 언제 어디까지 읽었는지를 남겨 집계가 확정인지 판단하게 한다.
 */
@Service
public class IssueCollector {

    private static final Logger log = LoggerFactory.getLogger(IssueCollector.class);

    /** 한 번에 읽을 Issue·PR 상한. 넘으면 불완전으로 표시한다. */
    private static final int MAX_ISSUES = 500;
    private static final int MAX_PULL_REQUESTS = 500;

    private final RepositoryContentGateway contents;
    private final IssueSnapshotRepository issues;
    private final ProjectRepository projects;
    private final ObjectMapper json;
    private final Clock clock;

    public IssueCollector(RepositoryContentGateway contents, IssueSnapshotRepository issues,
                          ProjectRepository projects, ObjectMapper json, Clock clock) {
        this.contents = contents;
        this.issues = issues;
        this.projects = projects;
        this.json = json;
        this.clock = clock;
    }

    /**
     * @return 상한에 걸리지 않고 전부 읽었으면 true
     */
    @Transactional
    public boolean collect(UUID projectId, RepositoryContentGateway.RepositoryRef repository) {
        Instant now = clock.instant();
        RepositoryContentGateway.IssuePage page = contents.listIssues(repository, MAX_ISSUES);
        List<RepositoryContentGateway.PullRequestSummary> pulls =
                contents.listPullRequests(repository, MAX_PULL_REQUESTS);

        // PR은 작업 ID로 묶는다. PR 수를 완료 수로 세지 않고 작업마다 어떤 PR이 있는지만 보여준다.
        Map<String, List<Map<String, Object>>> pullsByTask = new LinkedHashMap<>();
        for (RepositoryContentGateway.PullRequestSummary pull : pulls) {
            String taskSpecId = TaskIds.fromPullRequestTitle(pull.title());
            if (taskSpecId == null) {
                continue;
            }
            pullsByTask.computeIfAbsent(taskSpecId, key -> new ArrayList<>())
                    .add(Map.of("number", pull.number(), "state", pull.state(), "merged", pull.merged()));
        }

        issues.clearProject(projectId);
        for (RepositoryContentGateway.IssueSummary issue : page.items()) {
            String taskSpecId = TaskIds.fromIssueTitle(issue.title());
            issues.save(new IssueSnapshotEntity(projectId, issue.nodeId(), issue.number(), issue.title(),
                    issue.state(), issue.stateReason(), taskSpecId,
                    write(issue.assignees()), write(issue.labels()),
                    write(pullsByTask.getOrDefault(taskSpecId, List.of())), now));
        }

        projects.findById(projectId).ifPresent(project -> observed(project, now, page.complete()));
        log.info("Issue {}건을 읽었다 project={} complete={}", page.items().size(), projectId, page.complete());
        return page.complete();
    }

    private void observed(ProjectEntity project, Instant now, boolean complete) {
        project.issuesObserved(now, complete);
        projects.saveAndFlush(project);
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (RuntimeException e) {
            return "[]";
        }
    }
}
