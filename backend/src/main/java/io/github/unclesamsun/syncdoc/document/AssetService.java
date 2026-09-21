package io.github.unclesamsun.syncdoc.document;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.document.domain.AssetContentRepository;
import io.github.unclesamsun.syncdoc.document.domain.AssetEntity;
import io.github.unclesamsun.syncdoc.document.domain.AssetRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotRepository;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-020. 첨부 내려주기.
 *
 * <p>요청마다 프로젝트를 볼 수 있는지 다시 확인한다. 과거 게시본의 그림을 가리키는 주소로 들어와도
 * 같은 확인을 거치므로, 권한이 철회된 뒤에는 예전 주소로도 내용을 받을 수 없다.
 *
 * <p>없는 첨부와 권한 없는 첨부는 같은 응답이다. 응답 차이로 그림의 존재를 추론할 수 없게 한다.
 */
@Service
public class AssetService {

    private final ProjectService projects;
    private final DocumentSnapshotRepository snapshots;
    private final AssetRepository assets;
    private final AssetContentRepository contents;

    public AssetService(ProjectService projects, DocumentSnapshotRepository snapshots,
                        AssetRepository assets, AssetContentRepository contents) {
        this.projects = projects;
        this.snapshots = snapshots;
        this.assets = assets;
        this.contents = contents;
    }

    /** @param mime 저장할 때 정한 값만 나간다. 요청이 형식을 정하지 못한다 */
    public record AssetContent(String mime, byte[] bytes) {
    }

    @Transactional(readOnly = true)
    public AssetContent read(CurrentUser user, UUID projectId, UUID assetId, UUID snapshotId) {
        ProjectService.ProjectView project = projects.view(user, projectId);
        if (snapshotId == null) {
            // 어떤 게시본의 그림인지 밝히지 않은 요청이다. 계약이 snapshotId를 필수로 둔 이유다.
            throw new SnapshotRequiredException();
        }
        DocumentSnapshotEntity snapshot = snapshots.findById(snapshotId)
                .filter(candidate -> candidate.getProjectId().equals(project.id()))
                .orElseThrow(DocumentService.SnapshotGoneException::new);

        AssetEntity asset = assets.findByIdAndSnapshotId(assetId, snapshot.getId())
                .orElseThrow(DocumentService.DocumentNotFoundException::new);
        if (!AssetPolicy.isAllowed(asset.getMime())) {
            // 저장 뒤에 허용 목록이 좁아진 경우다. 지금 기준으로 내보내지 않는다.
            throw new DocumentService.DocumentNotFoundException();
        }
        byte[] bytes = contents.findById(asset.getStorageKey())
                .orElseThrow(DocumentService.DocumentNotFoundException::new)
                .getBytes();
        return new AssetContent(asset.getMime(), bytes);
    }

    public static class SnapshotRequiredException extends RuntimeException {
    }
}
