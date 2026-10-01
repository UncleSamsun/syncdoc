import { useState, type ReactNode } from "react";
import { Link, NavLink } from "react-router-dom";
import { useSession } from "../auth/useSession";
import DocumentTree from "../documents/DocumentTree";
import DocumentCatalog from "../documents/DocumentCatalog";
import { useChecklist } from "../spec/useChecklist";
import type { DocumentItem } from "../documents/types";
import type { ProjectItem, SyncStatus } from "../projects/types";
import { formatMoment, syncLabelOf, syncToneOf } from "../sync/syncLabels";
import AccountMenu from "./AccountMenu";
import BranchSwitcher from "./BranchSwitcher";

type Props = {
  project: ProjectItem;
  status?: SyncStatus;
  documents: DocumentItem[];
  /** The list may be historical; classification must use the same snapshot. */
  documentSnapshotId?: string | null;
  currentDocumentId?: string;
  currentPath?: string;
  /** 사이드바 `작업` 항목의 건수. 현황을 아직 읽지 않았으면 비운다. */
  taskCount?: number;
  /** 사이드바 `산출물` 항목의 오류 건수. 아직 읽지 않았으면 비운다. */
  checklistErrors?: number;
  active?: "overview" | "tasks" | "search" | "documents" | "checklist";
  children: ReactNode;
};

function savedDocumentView(projectId: string): "types" | "folders" {
  try { return localStorage.getItem(`syncdoc:document-view:${projectId}`) === "folders" ? "folders" : "types"; }
  catch { return "types"; }
}

/**
 * UI-000 공통 셸. 상단바와 사이드바는 화면이 바뀌어도 같은 자리에 그대로 있다.
 *
 * <p>프로젝트를 고르고 저장소를 연결하는 일은 사이드바가 아니라 UI-001이 전담한다. 사이드바에
 * 프로젝트 관련 동작 버튼을 두지 않는다.
 *
 * <p>이동 항목 넷(현황·작업·산출물·검색)은 어느 화면에서도 같은 자리에 있다.
 */
export default function AppShell({
  project,
  status,
  documents,
  documentSnapshotId,
  currentDocumentId,
  currentPath,
  taskCount,
  checklistErrors,
  active,
  children,
}: Props) {
  const session = useSession();
  const [viewPreference, setViewPreference] = useState(() => ({ projectId: project.id, view: savedDocumentView(project.id) }));
  const documentView = viewPreference.projectId === project.id ? viewPreference.view : savedDocumentView(project.id);
  const setDocumentView = (view: "types" | "folders") => {
    setViewPreference({ projectId: project.id, view });
    try { localStorage.setItem(`syncdoc:document-view:${project.id}`, view); } catch { /* UI still works without storage. */ }
  };
  const catalog = useChecklist(project.id, project.version);
  const shownSnapshotId = documentSnapshotId === undefined ? project.currentSnapshotId : documentSnapshotId;
  const types = catalog.state === "ready" && catalog.checklist.snapshotId === shownSnapshotId
    ? catalog.checklist.types : [];
  const syncState = status?.state ?? project.syncState;
  const label = syncLabelOf({ syncState, currentSnapshotId: project.currentSnapshotId });
  // 수집 중에는 어느 브랜치를 모으는 중인지 함께 보인다. 브랜치를 바꾼 직후 이전 게시본을
  // 보고 있는 사람이 무엇을 기다리는지 알 수 있어야 한다.
  const pill = syncState === "running" ? `${project.branch} ${label}` : label;

  return (
    <div className="shell">
      <header className="sh-top">
        <Link className="brand" to="/">
          SyncDoc
        </Link>
        <span className="sw" title={`연결한 저장소: ${project.fullName}`}>
          {project.fullName}
        </span>
        <BranchSwitcher
          project={project}
          csrfToken={session.state === "signed-in" ? session.me.csrfToken : ""}
        />
        <span className="rootpath" title={project.docsRoot}>{project.docsRoot}</span>
        <span className={`syncpill ${syncToneOf(syncState) === "fail" ? "stale" : ""}`}>
          <span className={`dot dot--${syncToneOf(syncState)}`} aria-hidden="true" />
          {pill}
          {status?.lastSuccessAt && <span className="mono">{formatMoment(status.lastSuccessAt)}</span>}
        </span>
        <Link className="gear" to={`/projects/${project.id}/settings`} title="연결 설정">
          설정
        </Link>
        {session.state === "signed-in" && (
          <AccountMenu login={session.me.login} csrfToken={session.me.csrfToken} />
        )}
      </header>

      <div className="shell-body">
        <nav className="sh-side" aria-label="프로젝트">
          <NavLink
            className="nav-a"
            to={`/projects/${project.id}`}
            end
            aria-current={active === "overview" ? "page" : undefined}
          >
            현황
          </NavLink>
          <NavLink
            className="nav-a"
            to={`/projects/${project.id}/tasks`}
            aria-current={active === "tasks" ? "page" : undefined}
          >
            작업
            {taskCount !== undefined && <span className="cnt">{taskCount}</span>}
          </NavLink>
          <NavLink
            className="nav-a"
            to={`/projects/${project.id}/checklist`}
            aria-current={active === "checklist" ? "page" : undefined}
          >
            산출물
            {checklistErrors !== undefined && checklistErrors > 0 && (
              <span className="cnt">오류 {checklistErrors}</span>
            )}
          </NavLink>
          <NavLink
            className="nav-a"
            to={`/projects/${project.id}/search`}
            aria-current={active === "search" ? "page" : undefined}
          >
            검색
          </NavLink>

          <div className="nav-grp">
            문서
            <span className="cnt">{documents.length}</span>
          </div>
          <div className="document-views" aria-label="문서 탐색 방식">
            <button type="button" aria-pressed={documentView === "types"} onClick={() => setDocumentView("types")}>종류별</button>
            <button type="button" aria-pressed={documentView === "folders"} onClick={() => setDocumentView("folders")}>폴더별</button>
          </div>
          {documentView === "types" && types.length > 0 ? (
            <DocumentCatalog key={project.id} projectId={project.id}
              snapshotId={shownSnapshotId && shownSnapshotId !== project.currentSnapshotId ? shownSnapshotId : undefined}
              documents={documents} types={types} currentDocumentId={currentDocumentId} />
          ) : <>
          {documentView === "types" && catalog.state === "ready" && types.length === 0 && (
            <p className="catalog-note">{catalog.checklist.snapshotId !== shownSnapshotId
              ? "이 게시본과 종류 정의가 달라 폴더로 표시합니다."
              : "종류 정의를 확인할 수 없어 폴더로 표시합니다."}</p>
          )}
          <DocumentTree
            projectId={project.id}
            snapshotId={shownSnapshotId && shownSnapshotId !== project.currentSnapshotId ? shownSnapshotId : undefined}
            items={documents}
            currentDocumentId={currentDocumentId}
            currentPath={currentPath}
          />
          </>}
        </nav>
        <main className="shell-main">{children}</main>
      </div>
    </div>
  );
}
