package io.github.unclesamsun.syncdoc.spec;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.document.DocumentService;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotEntity;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * API-025. 게시본에 저장해 둔 산출물 체크리스트를 읽는다.
 *
 * <p>여기서 다시 판정하지 않는다. 판정은 수집할 때 그 revision의 규칙 파일로 한 번 했고, 게시본은
 * 불변이므로 결과도 그 revision에 고정된다. 조회할 때마다 다시 판정하면 같은 게시본이 때에 따라
 * 다른 답을 낸다.
 */
@Service
public class SpecChecklistService {

    private final ProjectService projects;
    private final DocumentService documents;
    private final ObjectMapper json;

    public SpecChecklistService(ProjectService projects, DocumentService documents, ObjectMapper json) {
        this.projects = projects;
        this.documents = documents;
        this.json = json;
    }

    /**
     * @param snapshotId     결과가 속한 게시본
     * @param sourceRevision 그 게시본이 고정한 revision
     */
    public record ChecklistView(UUID snapshotId, String sourceRevision, String status,
                                String uncheckedReason, boolean truncated,
                                java.util.List<SpecChecklist.Finding> findings,
                                java.util.List<SpecChecklist.TypeResult> types) {
    }

    @Transactional(readOnly = true)
    public ChecklistView view(CurrentUser user, UUID projectId, UUID requestedSnapshotId) {
        ProjectService.ProjectView project = projects.view(user, projectId);
        DocumentSnapshotEntity snapshot = documents.snapshotFor(project, requestedSnapshotId)
                // 첫 수집 전이다. 빈 결과로 돌려주지 않는다. 결과가 없는 것과 미검사는 다르다.
                .orElseThrow(DocumentService.DocumentsNotReadyException::new);

        SpecChecklist checklist = json.readValue(snapshot.getChecklistJson(), SpecChecklist.class);
        return new ChecklistView(snapshot.getId(), snapshot.getSourceRevision(), checklist.status(),
                checklist.uncheckedReason(), checklist.truncated(), checklist.findings(),
                checklist.types());
    }
}
