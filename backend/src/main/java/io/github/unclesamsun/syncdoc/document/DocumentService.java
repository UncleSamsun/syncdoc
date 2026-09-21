package io.github.unclesamsun.syncdoc.document;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.document.domain.DocumentEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotRepository;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * API-017·API-018. 문서 목록과 본문.
 *
 * <p>두 계약 모두 프로젝트를 볼 수 있는지 먼저 확인한다. 이전 snapshot을 지정한 주소로 들어와도
 * 같은 확인을 다시 한다. 예전에 볼 수 있었다는 사실로 내용을 돌려주지 않는다.
 *
 * <p>변환은 수집 때 이미 끝났다. 여기서는 저장한 결과를 꺼내 줄 뿐 다시 변환하지 않는다.
 */
@Service
public class DocumentService {

    /** 계약 `## 공통`의 목록 기본값과 최대값이다. */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectService projects;
    private final DocumentSnapshotRepository snapshots;
    private final DocumentRepository documents;
    private final ObjectMapper json;

    public DocumentService(ProjectService projects, DocumentSnapshotRepository snapshots,
                           DocumentRepository documents, ObjectMapper json) {
        this.projects = projects;
        this.snapshots = snapshots;
        this.documents = documents;
        this.json = json;
    }

    public record DocumentItem(UUID id, String path, String title, String kind) {
    }

    public record DocumentListView(UUID snapshotId, List<DocumentItem> items, String nextCursor) {
    }

    /**
     * @param html     변환 결과. 화면은 이 값을 그대로 표시하고 원문을 다시 해석하지 않는다
     * @param diagrams 원문 그대로의 다이어그램. HTML에는 자리 표시만 들어 있다
     */
    public record DocumentView(UUID id, UUID snapshotId, String sourceRevision, String path, String title,
                               String specId, String kind, String html, Object headings, Object diagrams,
                               Object links, Object warnings) {
    }

    /** API-017. 첫 수집 전에는 빈 목록이다. 목록이 비어 있는 것은 오류가 아니다. */
    @Transactional(readOnly = true)
    public DocumentListView list(CurrentUser user, UUID projectId, UUID requestedSnapshotId, String cursor,
                                 Integer limit) {
        ProjectService.ProjectView project = projects.view(user, projectId);
        Optional<DocumentSnapshotEntity> snapshot = resolveSnapshot(project, requestedSnapshotId);
        if (snapshot.isEmpty()) {
            return new DocumentListView(null, List.of(), null);
        }

        int size = pageSize(limit);
        String after = decodeCursor(cursor);
        List<DocumentEntity> page = documents.findPage(snapshot.get().getId(), after, size + 1);
        boolean hasMore = page.size() > size;
        List<DocumentEntity> items = hasMore ? page.subList(0, size) : page;

        return new DocumentListView(snapshot.get().getId(),
                items.stream().map(document -> new DocumentItem(document.getId(), document.getPath(),
                        document.getTitle(), document.getKind())).toList(),
                hasMore ? encodeCursor(items.getLast().getPath()) : null);
    }

    /** API-018. */
    @Transactional(readOnly = true)
    public DocumentView view(CurrentUser user, UUID projectId, UUID documentId, UUID requestedSnapshotId) {
        ProjectService.ProjectView project = projects.view(user, projectId);
        DocumentSnapshotEntity snapshot = resolveSnapshot(project, requestedSnapshotId)
                .orElseThrow(DocumentsNotReadyException::new);

        DocumentEntity document = documents.findById(documentId)
                .filter(candidate -> candidate.getSnapshotId().equals(snapshot.getId()))
                .orElseThrow(DocumentNotFoundException::new);
        if (!DocumentEntity.VALID.equals(document.getState()) || document.getHtml() == null) {
            // 변환 결과가 없는 문서다. 본문 대신 표시할 수 없다는 사실을 알린다.
            throw new DocumentNotRenderableException();
        }

        return new DocumentView(document.getId(), snapshot.getId(), snapshot.getSourceRevision(),
                document.getPath(), document.getTitle(), document.getSpecId(), document.getKind(),
                document.getHtml(), read(document.getHeadingsJson()), read(document.getDiagramsJson()),
                read(document.getLinksJson()), read(document.getWarningsJson()));
    }

    /**
     * 지정한 게시본을 찾는다. 지정이 없으면 현재 게시본이다.
     *
     * @throws SnapshotGoneException 지정한 게시본이 이 프로젝트에 없을 때. 보관에서 지워졌다는 뜻이다
     */
    private Optional<DocumentSnapshotEntity> resolveSnapshot(ProjectService.ProjectView project,
                                                             UUID requestedSnapshotId) {
        if (requestedSnapshotId == null) {
            return project.currentSnapshotId() == null
                    ? Optional.empty() : snapshots.findById(project.currentSnapshotId());
        }
        return Optional.of(snapshots.findById(requestedSnapshotId)
                .filter(snapshot -> snapshot.getProjectId().equals(project.id()))
                .filter(DocumentSnapshotEntity::isComplete)
                .orElseThrow(SnapshotGoneException::new));
    }

    private static int pageSize(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    /** cursor는 서버가 발급한 불투명 문자열이다. 안에 경로가 들어 있다는 사실에 화면이 기대지 않게 한다. */
    private static String encodeCursor(String path) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(path.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException();
        }
    }

    private Object read(String value) {
        try {
            return json.readTree(value);
        } catch (RuntimeException e) {
            return json.createArrayNode();
        }
    }

    /** 첫 수집이 끝나기 전이다. 화면은 이 상태를 대기로 보여주고 문서 없음으로 쓰지 않는다. */
    public static class DocumentsNotReadyException extends RuntimeException {
    }

    /** 없는 문서와 권한이 없는 문서는 같은 예외다. 둘을 구분할 단서를 만들지 않는다. */
    public static class DocumentNotFoundException extends RuntimeException {
    }

    /** 지정한 게시본이 보관에서 지워졌다. 막다른 길이 되지 않게 최신 목록으로 안내한다. */
    public static class SnapshotGoneException extends RuntimeException {
    }

    /** 변환 결과가 없어 본문을 보여줄 수 없다. */
    public static class DocumentNotRenderableException extends RuntimeException {
    }

    public static class InvalidCursorException extends RuntimeException {
    }
}
