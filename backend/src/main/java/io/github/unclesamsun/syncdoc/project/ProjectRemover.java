package io.github.unclesamsun.syncdoc.project;

import io.github.unclesamsun.syncdoc.dashboard.domain.IssueSnapshotRepository;
import io.github.unclesamsun.syncdoc.dashboard.domain.TaskRepository;
import io.github.unclesamsun.syncdoc.document.domain.AssetContentRepository;
import io.github.unclesamsun.syncdoc.document.domain.AssetRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotRepository;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import io.github.unclesamsun.syncdoc.sync.domain.SyncJobRepository;
import io.github.unclesamsun.syncdoc.sync.domain.SyncRunRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-026이 프로젝트와 그 프로젝트가 수집해 둔 자료를 지운다.
 *
 * <p>끊었는데 문서 본문이 서버에 남아 있으면 끊었다는 말과 실제가 다르다. 게시본·문서·첨부·
 * 작업·Issue 사본은 모두 GitHub 원문에서 다시 만들 수 있는 파생 자료라 남길 이유도 없다.
 *
 * <p>순서는 외래키가 가리키는 반대 방향이다. `projects`가 `document_snapshots`를 가리키고
 * `document_snapshots`가 `projects`를 가리키므로, 게시본을 지우기 전에 현재 게시본 참조를 먼저
 * 끊는다. GitHub에는 아무것도 쓰지 않는다.
 */
@Component
public class ProjectRemover {

    private final ProjectRepository projects;
    private final DocumentSnapshotRepository snapshots;
    private final DocumentRepository documents;
    private final AssetRepository assets;
    private final AssetContentRepository assetContents;
    private final TaskRepository tasks;
    private final IssueSnapshotRepository issues;
    private final SyncJobRepository jobs;
    private final SyncRunRepository runs;

    public ProjectRemover(ProjectRepository projects, DocumentSnapshotRepository snapshots,
                          DocumentRepository documents, AssetRepository assets,
                          AssetContentRepository assetContents, TaskRepository tasks,
                          IssueSnapshotRepository issues, SyncJobRepository jobs,
                          SyncRunRepository runs) {
        this.projects = projects;
        this.snapshots = snapshots;
        this.documents = documents;
        this.assets = assets;
        this.assetContents = assetContents;
        this.tasks = tasks;
        this.issues = issues;
        this.jobs = jobs;
        this.runs = runs;
    }

    @Transactional
    public void remove(UUID projectId) {
        tasks.deleteByProjectId(projectId);
        documents.deleteByProjectId(projectId);
        assets.deleteByProjectId(projectId);
        issues.clearProject(projectId);

        // 이력이 작업을 가리키므로 이력을 먼저 지운다. 진행 중이거나 예정된 수집은 작업이
        // 사라지면서 취소된다. 임대를 쥔 worker는 게시할 작업을 찾지 못해 멈춘다.
        runs.deleteByProjectId(projectId);
        jobs.deleteByProjectId(projectId);

        projects.clearCurrentSnapshot(projectId);
        snapshots.deleteByProjectId(projectId);
        projects.deleteById(projectId);

        // 같은 바이트를 다른 프로젝트가 가리킬 수 있다. 가리키는 첨부가 없어진 것만 지운다.
        assetContents.deleteOrphans();
    }
}
