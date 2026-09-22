package io.github.unclesamsun.syncdoc.sync;

import tools.jackson.databind.ObjectMapper;
import io.github.unclesamsun.syncdoc.dashboard.IssueCollector;
import io.github.unclesamsun.syncdoc.dashboard.TaskIds;
import io.github.unclesamsun.syncdoc.dashboard.domain.TaskEntity;
import io.github.unclesamsun.syncdoc.dashboard.domain.TaskRepository;
import io.github.unclesamsun.syncdoc.document.AssetPolicy;
import io.github.unclesamsun.syncdoc.document.DocumentVersions;
import io.github.unclesamsun.syncdoc.document.MarkdownRenderService;
import io.github.unclesamsun.syncdoc.spec.ApplySpecTable;
import io.github.unclesamsun.syncdoc.spec.ChecklistChecker;
import io.github.unclesamsun.syncdoc.spec.SpecChecklist;
import io.github.unclesamsun.syncdoc.spec.SpecFormat;
import io.github.unclesamsun.syncdoc.spec.SpecFormatReader;
import io.github.unclesamsun.syncdoc.document.domain.AssetContentEntity;
import io.github.unclesamsun.syncdoc.document.domain.AssetContentRepository;
import io.github.unclesamsun.syncdoc.document.domain.AssetEntity;
import io.github.unclesamsun.syncdoc.document.domain.AssetRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotRepository;
import io.github.unclesamsun.syncdoc.github.GitHubGatewayNotConfiguredException;
import io.github.unclesamsun.syncdoc.github.GitHubLookupFailedException;
import io.github.unclesamsun.syncdoc.github.GitHubRateLimitedException;
import io.github.unclesamsun.syncdoc.github.RepositoryContentGateway;
import io.github.unclesamsun.syncdoc.project.domain.InstallationRepository;
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 수집 한 번을 수행한다.
 *
 * <p>순서가 규칙이다. 브랜치가 가리키는 commit을 먼저 고정하고, 그 revision으로만 읽고, 문서를
 * 모두 모은 뒤에야 게시한다. 도중에 실패하면 미완성 게시본은 그대로 두고 현재 게시본은 건드리지 않는다.
 * 그래서 실패한 수집이 마지막 정상 데이터를 덮어쓸 수 없다.
 *
 * <p>이 클래스에는 트랜잭션이 없다. GitHub 호출이 긴 동안 DB 트랜잭션을 열어 두지 않기 위해서다.
 * 상태를 바꾸는 각 단계는 {@link SyncQueue}의 짧은 트랜잭션에서 일어난다.
 */
@Service
public class SyncWorker {

    private static final Logger log = LoggerFactory.getLogger(SyncWorker.class);

    /** 작업을 뽑을 문서 종류와 제목 단계. 규칙 파일이 정한 작업계획 문서의 형식이다. */
    private static final String TASKS_DOCUMENT_KIND = "tasks";
    /** 규칙 파일은 저장소 루트의 고정 경로다. 프로젝트가 정한 문서 경로와 무관하다. */
    private static final String SPEC_FORMAT_PATH = "rules/spec-format.json";
    private static final String PROJECT_SETTINGS_PATH = "rules/project-settings.md";
    private static final int TASK_HEADING_LEVEL = 2;

    private final SyncQueue queue;
    private final ProjectRepository projects;
    private final InstallationRepository installations;
    private final RepositoryContentGateway contents;
    private final DocumentSnapshotRepository snapshots;
    private final DocumentRepository documents;
    private final AssetRepository assets;
    private final AssetContentRepository assetContents;
    private final MarkdownRenderService renderer;
    private final TaskRepository tasks;
    private final IssueCollector issueCollector;
    private final SpecFormatReader specFormats;
    private final ChecklistChecker checklistChecker;
    private final SyncProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public SyncWorker(SyncQueue queue, ProjectRepository projects, InstallationRepository installations,
                      RepositoryContentGateway contents, DocumentSnapshotRepository snapshots,
                      DocumentRepository documents, AssetRepository assets,
                      AssetContentRepository assetContents, MarkdownRenderService renderer,
                      TaskRepository tasks, IssueCollector issueCollector,
                      SpecFormatReader specFormats, ChecklistChecker checklistChecker,
                      SyncProperties properties, ObjectMapper json, Clock clock) {
        this.queue = queue;
        this.projects = projects;
        this.installations = installations;
        this.contents = contents;
        this.snapshots = snapshots;
        this.documents = documents;
        this.assets = assets;
        this.assetContents = assetContents;
        this.renderer = renderer;
        this.tasks = tasks;
        this.issueCollector = issueCollector;
        this.specFormats = specFormats;
        this.checklistChecker = checklistChecker;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    /** @return 실행할 작업이 있었으면 true */
    public boolean runOnce() {
        Optional<SyncQueue.Lease> lease = queue.claimNext();
        lease.ifPresent(this::run);
        return lease.isPresent();
    }

    public void run(SyncQueue.Lease lease) {
        try {
            collect(lease);
        } catch (GitHubRateLimitedException e) {
            queue.fail(lease, "GITHUB_RATE_LIMITED", e.retryAfter(), diagnostics(Map.of()));
        } catch (RepositoryContentGateway.DocsRootMissingException e) {
            queue.fail(lease, "DOCS_ROOT_MISSING", null, diagnostics(Map.of("message", e.getMessage())));
        } catch (RepositoryContentGateway.TooManyDocumentsException e) {
            queue.fail(lease, "TOO_MANY_DOCUMENTS", null,
                    diagnostics(Map.of("limit", properties.maxDocuments())));
        } catch (RepositoryContentGateway.DocumentTooLargeException e) {
            queue.fail(lease, "DOCUMENT_TOO_LARGE", null,
                    diagnostics(Map.of("limit", properties.maxDocumentSize())));
        } catch (DocumentRenderFailedException e) {
            queue.fail(lease, "DOCUMENT_RENDER_FAILED", null,
                    diagnostics(Map.of("path", e.path(), "reason", e.getMessage())));
        } catch (GitHubGatewayNotConfiguredException e) {
            queue.fail(lease, "INSTALLATION_TOKEN_UNAVAILABLE", null, diagnostics(Map.of()));
        } catch (GitHubLookupFailedException e) {
            queue.fail(lease, "GITHUB_UNAVAILABLE", null, diagnostics(Map.of()));
        } catch (RuntimeException e) {
            // 예상하지 못한 실패도 큐를 멈추게 두지 않는다. 진단에는 예외 메시지를 넣지 않는다.
            log.warn("수집 실패 project={} job={}", lease.projectId(), lease.jobId(), e);
            queue.fail(lease, "COLLECTION_FAILED", null, diagnostics(Map.of()));
        }
    }

    private void collect(SyncQueue.Lease lease) {
        ProjectEntity project = projects.findById(lease.projectId()).orElse(null);
        if (project == null) {
            queue.fail(lease, "PROJECT_MISSING", null, diagnostics(Map.of()));
            return;
        }
        String installationId = installations.findById(project.getInstallationId())
                .orElseThrow(() -> new GitHubLookupFailedException("설치 정보를 찾을 수 없다"))
                .getGithubInstallationId();

        // 저장소는 여기서 한 번만 해소한다. 문서마다 다시 해소하면 요청이 문서 수만큼 늘어난다.
        RepositoryContentGateway.RepositoryRef repository =
                contents.open(installationId, project.getGithubRepositoryId());
        String revision = contents.headRevision(repository, project.getBranch());
        queue.rememberTargetRevision(lease, revision);

        // Issue는 문서와 상관없이 바뀐다. 문서가 그대로여서 일찍 끝나는 경우에도 읽어야
        // 현황이 멈추지 않는다. 실제 실행에서 이 순서를 놓쳐 Issue가 한 번도 갱신되지 않았다.
        boolean issuesComplete = collectIssues(project.getId(), repository);

        Optional<DocumentSnapshotEntity> existing = snapshots
                .findByProjectIdAndSourceRevisionAndRendererVersionAndPolicyVersion(
                        project.getId(), revision, DocumentVersions.RENDERER, DocumentVersions.POLICY);
        if (existing.isPresent() && existing.get().isComplete()) {
            // 같은 revision을 같은 규칙으로 이미 만들었다. 다시 변환하지 않는다.
            if (revision.equals(currentRevision(project))) {
                queue.succeedUnchanged(lease, revision,
                        diagnostics(Map.of("unchanged", true, "issuesComplete", issuesComplete)));
            } else {
                queue.publish(lease, existing.get().getId(), revision,
                        diagnostics(Map.of("reusedSnapshot", true, "issuesComplete", issuesComplete)));
            }
            return;
        }

        DocumentSnapshotEntity snapshot = existing.orElseGet(() -> snapshots.saveAndFlush(
                new DocumentSnapshotEntity(project.getId(), revision, DocumentVersions.RENDERER,
                        DocumentVersions.POLICY, clock.instant())));
        if (existing.isPresent()) {
            // 앞선 시도가 중간에 멈춘 게시본이다. 절반만 남은 문서와 첨부를 지우고 처음부터 채운다.
            tasks.deleteBySnapshotId(snapshot.getId());
            documents.deleteBySnapshotId(snapshot.getId());
            assets.deleteBySnapshotId(snapshot.getId());
        }

        List<RepositoryContentGateway.SourceFile> files = contents.listDocuments(repository, revision,
                project.getDocsRoot(), properties.maxDocuments());

        // 첨부를 먼저 담는다. 문서를 변환할 때 그림의 주소를 이미 알고 있어야 하기 때문이다.
        Map<String, UUID> assetIdsByPath = collectAssets(repository, revision, project, snapshot.getId());

        // 링크를 같은 게시본의 다른 문서 주소로 바꾸려면 저장하기 전에 서로의 id를 알아야 한다.
        Map<String, UUID> idsByPath = new LinkedHashMap<>();
        files.forEach(file -> idsByPath.put(file.path(), UUID.randomUUID()));
        MarkdownRenderService.LinkTargets targets = new MarkdownRenderService.LinkTargets() {

            @Override
            public UUID documentIdFor(String repositoryPath) {
                return idsByPath.get(repositoryPath);
            }

            @Override
            public UUID assetIdFor(String repositoryPath) {
                return assetIdsByPath.get(repositoryPath);
            }
        };
        Set<String> specIds = new HashSet<>();
        // 규약 판정은 원문을 봐야 한다. 게시본에는 변환 결과만 남으므로 지금 모아 둔다.
        List<ChecklistChecker.SourceDocument> forChecklist = new ArrayList<>();

        int stored = 0;
        for (RepositoryContentGateway.SourceFile file : files) {
            if (file.size() > properties.maxDocumentSize()) {
                throw new RepositoryContentGateway.DocumentTooLargeException(file.path(),
                        properties.maxDocumentSize());
            }
            String text = contents.readText(repository, file.blobSha(), properties.maxDocumentSize());
            UUID documentId = idsByPath.get(file.path());
            MarkdownRenderService.RenderedDocument rendered =
                    render(project.getId(), snapshot.getId(), file.path(), text, targets, specIds);
            documents.save(toEntity(documentId, snapshot.getId(), file.path(), text, rendered));
            forChecklist.add(new ChecklistChecker.SourceDocument(documentId.toString(), file.path(), text));
            // 작업계획 문서의 제목이 작업 목록의 정본이다. 다른 종류의 문서에서는 뽑지 않는다.
            if (TASKS_DOCUMENT_KIND.equals(rendered.kind())) {
                saveTasks(snapshot.getId(), documentId, rendered);
            }
            stored++;
            if (stored % 20 == 0 && !queue.renew(lease.jobId(), lease.token())) {
                // 임대를 잃었다. 다른 worker가 같은 작업을 다시 하고 있으므로 여기서 멈춘다.
                log.info("임대를 잃어 수집을 중단한다 project={} job={}", lease.projectId(), lease.jobId());
                return;
            }
        }
        SpecChecklist checklist = checklist(repository, revision, forChecklist);
        snapshot.checklist(json.writeValueAsString(checklist));
        snapshots.save(snapshot);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("documents", stored);
        summary.put("checklist", checklist.status());
        summary.put("issuesComplete", issuesComplete);
        summary.put("assets", assetIdsByPath.size());
        summary.put("revision", revision);
        if (!queue.publish(lease, snapshot.getId(), revision, diagnostics(summary))) {
            log.info("임대를 잃어 게시하지 않는다 project={} job={}", lease.projectId(), lease.jobId());
        }
    }

    /**
     * 산출물 체크리스트(API-025)를 판정한다.
     *
     * <p>규칙 파일은 저장소 루트의 고정 경로에 있다. 프로젝트가 정한 문서 경로와 무관하다.
     * 읽지 못하면 미검사로 담는다. 판정하지 못한 것을 통과로 보이게 하지 않는다.
     */
    private SpecChecklist checklist(RepositoryContentGateway.RepositoryRef repository, String revision,
                                    List<ChecklistChecker.SourceDocument> sources) {
        SpecFormat format = contents
                .readTextAt(repository, revision, SPEC_FORMAT_PATH, properties.maxDocumentSize())
                .flatMap(specFormats::read)
                .orElse(null);
        ApplySpecTable table = contents
                .readTextAt(repository, revision, PROJECT_SETTINGS_PATH, properties.maxDocumentSize())
                .flatMap(ApplySpecTable::read)
                .orElse(null);
        return checklistChecker.check(format, table, sources);
    }

    /**
     * 받아들일 수 있는 첨부를 담는다.
     *
     * <p>받지 않는 형식, 상한을 넘는 크기, 이름과 내용이 어긋나는 파일은 건너뛴다. 첨부 하나 때문에
     * 문서 전체를 못 읽게 만들지 않는다. 건너뛴 그림은 문서 변환에서 경고로 드러난다.
     *
     * @return 저장소 경로에서 첨부 id를 찾는 표
     */
    private Map<String, UUID> collectAssets(RepositoryContentGateway.RepositoryRef repository,
                                            String revision, ProjectEntity project, UUID snapshotId) {
        Map<String, UUID> idsByPath = new LinkedHashMap<>();
        List<RepositoryContentGateway.SourceFile> candidates = contents.listAssets(repository, revision,
                project.getDocsRoot(), properties.maxAssets());

        for (RepositoryContentGateway.SourceFile file : candidates) {
            String mime = AssetPolicy.mimeFor(file.path()).orElse(null);
            if (mime == null || file.size() > properties.maxAssetSize()) {
                continue;
            }
            byte[] bytes = contents.readBytes(repository, file.blobSha(), properties.maxAssetSize());
            if (!AssetPolicy.contentMatches(mime, bytes)) {
                log.info("이름과 내용이 다른 첨부를 건너뛴다 path={}", file.path());
                continue;
            }
            String hash = sha256(bytes);
            if (!assetContents.existsById(hash)) {
                // 같은 그림이 여러 게시본에 나와도 바이트는 한 벌만 남는다.
                assetContents.save(new AssetContentEntity(hash, bytes, clock.instant()));
            }
            UUID id = UUID.randomUUID();
            assets.save(new AssetEntity(id, snapshotId, file.path(), mime, hash, bytes.length));
            idsByPath.put(file.path(), id);
        }
        return idsByPath;
    }

    /**
     * 문서 하나를 변환한다.
     *
     * <p>변환·정화에 실패하거나 규약 ID가 겹치면 예외를 던져 수집 전체를 실패로 만든다.
     * 데이터 설계가 정한 대로, 그런 게시본으로는 전환하지 않고 마지막 정상 게시본을 유지한다.
     * 문제가 있는 문서 하나를 조용히 빼고 게시하면 문서가 사라진 것처럼 보인다.
     */
    private MarkdownRenderService.RenderedDocument render(UUID projectId, UUID snapshotId, String path,
                                                          String text,
                                                          MarkdownRenderService.LinkTargets targets,
                                                          Set<String> specIds) {
        MarkdownRenderService.RenderedDocument rendered;
        try {
            rendered = renderer.render(projectId, snapshotId, path, text, targets);
        } catch (RuntimeException e) {
            throw new DocumentRenderFailedException(path, "변환에 실패했다");
        }
        if (rendered.specId() != null && !specIds.add(rendered.specId())) {
            throw new DocumentRenderFailedException(path, "규약 ID " + rendered.specId() + "가 겹친다");
        }
        return rendered;
    }

    private DocumentEntity toEntity(UUID documentId, UUID snapshotId, String path, String text,
                                    MarkdownRenderService.RenderedDocument rendered) {
        DocumentEntity document = new DocumentEntity(documentId, snapshotId, path, rendered.title(),
                sha256(text), rendered.plainText());
        document.rendered(rendered.title(), rendered.specId(), rendered.kind(), rendered.html(),
                rendered.plainText(), write(rendered.headings()), write(rendered.diagrams()),
                write(rendered.links()), write(rendered.warnings()));
        return document;
    }

    /**
     * 작업계획 문서의 `## TASK-NNN` 제목을 작업으로 남긴다.
     *
     * <p>Issue 연결은 여기서 하지 않는다. 작업은 명세가, 실행 상태는 GitHub가 정본이며 둘을 잇는 일은
     * 조회 시점에 한다. 수집 때 붙여 두면 Issue 상태가 바뀌어도 옛 연결이 남는다.
     */
    private void saveTasks(UUID snapshotId, UUID documentId,
                           MarkdownRenderService.RenderedDocument rendered) {
        for (TaskIds.ExtractedTask task : TaskIds.fromHeadings(rendered.headings(), TASK_HEADING_LEVEL)) {
            tasks.save(new TaskEntity(snapshotId, task.taskSpecId(), documentId, task.anchor(),
                    task.title(), true));
        }
    }

    /** 변환 단계의 실패. 어느 문서에서 났는지만 남기고 원문이나 예외 내용은 담지 않는다. */
    static class DocumentRenderFailedException extends RuntimeException {

        private final String path;

        DocumentRenderFailedException(String path, String reason) {
            super(reason);
            this.path = path;
        }

        String path() {
            return path;
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (RuntimeException e) {
            return "[]";
        }
    }

    /**
     * Issue를 읽는다. 읽지 못해도 수집을 실패로 만들지 않는다 — 권한이 아직 없을 수 있고,
     * 그때 문서 현황까지 못 보게 되는 편이 더 나쁘다. 집계가 확정인지는 관찰 기록으로 판단한다.
     */
    private boolean collectIssues(UUID projectId, RepositoryContentGateway.RepositoryRef repository) {
        try {
            return issueCollector.collect(projectId, repository);
        } catch (RuntimeException e) {
            log.info("Issue를 읽지 못해 집계를 불완전으로 둔다 project={}", projectId);
            return false;
        }
    }

    private String currentRevision(ProjectEntity project) {
        if (project.getCurrentSnapshotId() == null) {
            return null;
        }
        return snapshots.findById(project.getCurrentSnapshotId())
                .map(DocumentSnapshotEntity::getSourceRevision)
                .orElse(null);
    }

    static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("해시를 계산하지 못했다", e);
        }
    }

    /** 진단에는 저장소 안의 정보와 코드만 담는다. 토큰·서버 경로·예외 stack은 담지 않는다. */
    private String diagnostics(Map<String, Object> values) {
        try {
            return json.writeValueAsString(values);
        } catch (RuntimeException e) {
            return "{}";
        }
    }
}
