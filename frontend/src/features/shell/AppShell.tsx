import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import DocumentTree from "../documents/DocumentTree";
import type { DocumentItem } from "../documents/types";
import type { ProjectItem, SyncStatus } from "../projects/types";
import { formatMoment, syncLabelOf, syncToneOf } from "../sync/syncLabels";

type Props = {
  project: ProjectItem;
  status?: SyncStatus;
  documents: DocumentItem[];
  currentDocumentId?: string;
  currentPath?: string;
  children: ReactNode;
};

/**
 * UI-000 공통 셸. 상단바와 사이드바는 화면이 바뀌어도 같은 자리에 그대로 있다.
 *
 * <p>프로젝트를 고르고 저장소를 연결하는 일은 사이드바가 아니라 UI-001이 전담한다. 사이드바에
 * 프로젝트 관련 동작 버튼을 두지 않는다.
 *
 * <p>현황·작업·검색 이동 항목은 그 화면을 만드는 TASK-007에서 붙인다. 갈 수 없는 항목을 먼저
 * 보여주지 않는다.
 */
export default function AppShell({
  project,
  status,
  documents,
  currentDocumentId,
  currentPath,
  children,
}: Props) {
  const syncState = status?.state ?? project.syncState;
  const label = syncLabelOf({ syncState, currentSnapshotId: project.currentSnapshotId });

  return (
    <div className="shell">
      <header className="sh-top">
        <Link className="brand" to="/">
          SyncDoc
        </Link>
        <span className="sw" title="연결한 저장소">
          {project.fullName}
        </span>
        <span className="sw sw--br" title="기준 브랜치">
          {project.branch}
        </span>
        <span className="rootpath">{project.docsRoot}</span>
        <span className={`syncpill ${syncToneOf(syncState) === "fail" ? "stale" : ""}`}>
          <span className={`dot dot--${syncToneOf(syncState)}`} aria-hidden="true" />
          {label}
          {status?.lastSuccessAt && <span className="mono">{formatMoment(status.lastSuccessAt)}</span>}
        </span>
      </header>

      <div className="shell-body">
        <nav className="sh-side" aria-label="문서">
          <div className="nav-grp">
            문서
            <span className="cnt">{documents.length}</span>
          </div>
          <DocumentTree
            projectId={project.id}
            items={documents}
            currentDocumentId={currentDocumentId}
            currentPath={currentPath}
          />
        </nav>
        <main className="shell-main">{children}</main>
      </div>
    </div>
  );
}
