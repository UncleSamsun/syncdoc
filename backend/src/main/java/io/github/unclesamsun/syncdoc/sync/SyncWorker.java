package io.github.unclesamsun.syncdoc.sync;

import tools.jackson.databind.ObjectMapper;
import io.github.unclesamsun.syncdoc.document.DocumentVersions;
import io.github.unclesamsun.syncdoc.document.MarkdownRenderService;
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

    private final SyncQueue queue;
    private final ProjectRepository projects;
    private final InstallationRepository installations;
    private final RepositoryContentGateway contents;
    private final DocumentSnapshotRepository snapshots;
    private final DocumentRepository documents;
    private final MarkdownRenderService renderer;
    private final SyncProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public SyncWorker(SyncQueue queue, ProjectRepository projects, InstallationRepository installations,
                      RepositoryContentGateway contents, DocumentSnapshotRepository snapshots,
                      DocumentRepository documents, MarkdownRenderService renderer,
                      SyncProperties properties, ObjectMapper json, Clock clock) {
        this.queue = queue;
        this.projects = projects;
        this.installations = installations;
        this.contents = contents;
        this.snapshots = snapshots;
        this.documents = documents;
        this.renderer = renderer;
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

        Optional<DocumentSnapshotEntity> existing = snapshots
                .findByProjectIdAndSourceRevisionAndRendererVersionAndPolicyVersion(
                        project.getId(), revision, DocumentVersions.RENDERER, DocumentVersions.POLICY);
        if (existing.isPresent() && existing.get().isComplete()) {
            // 같은 revision을 같은 규칙으로 이미 만들었다. 다시 변환하지 않는다.
            if (revision.equals(currentRevision(project))) {
                queue.succeedUnchanged(lease, revision, diagnostics(Map.of("unchanged", true)));
            } else {
                queue.publish(lease, existing.get().getId(), revision,
                        diagnostics(Map.of("reusedSnapshot", true)));
            }
            return;
        }

        DocumentSnapshotEntity snapshot = existing.orElseGet(() -> snapshots.saveAndFlush(
                new DocumentSnapshotEntity(project.getId(), revision, DocumentVersions.RENDERER,
                        DocumentVersions.POLICY, clock.instant())));
        if (existing.isPresent()) {
            // 앞선 시도가 중간에 멈춘 게시본이다. 절반만 남은 문서를 지우고 처음부터 채운다.
            documents.deleteBySnapshotId(snapshot.getId());
        }

        List<RepositoryContentGateway.SourceFile> files = contents.listDocuments(repository, revision,
                project.getDocsRoot(), properties.maxDocuments());

        // 링크를 같은 게시본의 다른 문서 주소로 바꾸려면 저장하기 전에 서로의 id를 알아야 한다.
        Map<String, UUID> idsByPath = new LinkedHashMap<>();
        files.forEach(file -> idsByPath.put(file.path(), UUID.randomUUID()));
        MarkdownRenderService.LinkTargets targets = idsByPath::get;
        Set<String> specIds = new HashSet<>();

        int stored = 0;
        for (RepositoryContentGateway.SourceFile file : files) {
            if (file.size() > properties.maxDocumentSize()) {
                throw new RepositoryContentGateway.DocumentTooLargeException(file.path(),
                        properties.maxDocumentSize());
            }
            String text = contents.readText(repository, file.blobSha(), properties.maxDocumentSize());
            documents.save(convert(project.getId(), snapshot.getId(), idsByPath.get(file.path()),
                    file.path(), text, targets, specIds));
            stored++;
            if (stored % 20 == 0 && !queue.renew(lease.jobId(), lease.token())) {
                // 임대를 잃었다. 다른 worker가 같은 작업을 다시 하고 있으므로 여기서 멈춘다.
                log.info("임대를 잃어 수집을 중단한다 project={} job={}", lease.projectId(), lease.jobId());
                return;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("documents", stored);
        summary.put("revision", revision);
        if (!queue.publish(lease, snapshot.getId(), revision, diagnostics(summary))) {
            log.info("임대를 잃어 게시하지 않는다 project={} job={}", lease.projectId(), lease.jobId());
        }
    }

    /**
     * 문서 하나를 변환해 저장할 형태로 만든다.
     *
     * <p>변환·정화에 실패하거나 규약 ID가 겹치면 예외를 던져 수집 전체를 실패로 만든다.
     * 데이터 설계가 정한 대로, 그런 게시본으로는 전환하지 않고 마지막 정상 게시본을 유지한다.
     * 문제가 있는 문서 하나를 조용히 빼고 게시하면 문서가 사라진 것처럼 보인다.
     */
    private DocumentEntity convert(UUID projectId, UUID snapshotId, UUID documentId, String path,
                                   String text, MarkdownRenderService.LinkTargets targets,
                                   Set<String> specIds) {
        MarkdownRenderService.RenderedDocument rendered;
        try {
            rendered = renderer.render(projectId, path, text, targets);
        } catch (RuntimeException e) {
            throw new DocumentRenderFailedException(path, "변환에 실패했다");
        }
        if (rendered.specId() != null && !specIds.add(rendered.specId())) {
            throw new DocumentRenderFailedException(path, "규약 ID " + rendered.specId() + "가 겹친다");
        }
        DocumentEntity document = new DocumentEntity(documentId, snapshotId, path, rendered.title(),
                sha256(text), rendered.plainText());
        document.rendered(rendered.title(), rendered.specId(), rendered.kind(), rendered.html(),
                rendered.plainText(), write(rendered.headings()), write(rendered.diagrams()),
                write(rendered.links()), write(rendered.warnings()));
        return document;
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

    private String currentRevision(ProjectEntity project) {
        if (project.getCurrentSnapshotId() == null) {
            return null;
        }
        return snapshots.findById(project.getCurrentSnapshotId())
                .map(DocumentSnapshotEntity::getSourceRevision)
                .orElse(null);
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
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
