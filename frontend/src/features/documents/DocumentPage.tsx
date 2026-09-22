import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { apiGet, isApiError } from "../../shared/api/client";
import type { ProjectItem, SyncStatus } from "../projects/types";
import AppShell from "../shell/AppShell";
import { errorCountOf } from "../spec/types";
import { useChecklist } from "../spec/useChecklist";
import { FirstSyncWaiting, StaleBanner } from "../sync/SyncStates";
import DocumentBody from "./DocumentBody";
import {
  AccessUnavailable,
  DocumentMissing,
  DocumentUnreadable,
  SnapshotGone,
} from "./DocumentStates";
import { useDocument, useDocumentList } from "./useDocument";

/**
 * UI-002 자리를 대신하는 프로젝트 진입과 UI-003 문서 본문.
 *
 * <p>문서를 고르지 않았으면 목록만 있는 셸을 보여주고, 첫 수집 전에는 UI-007을 보여준다.
 * 현황 화면(UI-002)은 TASK-007에서 이 자리에 들어온다.
 */
export default function DocumentPage() {
  const { projectId, documentId } = useParams();
  const [params] = useSearchParams();
  const snapshotId = params.get("snapshotId");

  const [project, setProject] = useState<ProjectItem | null>(null);
  const [status, setStatus] = useState<SyncStatus | undefined>();
  const [projectError, setProjectError] = useState<"missing" | "unavailable" | undefined>();

  const { list } = useDocumentList(projectId, snapshotId);
  // 사이드바 건수는 어느 화면에서도 같아야 한다. 한 화면에만 두면 옮길 때마다 값이 사라진다.
  const checklist = useChecklist(projectId);
  const document = useDocument(projectId, documentId, snapshotId);

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let cancelled = false;
    apiGet<ProjectItem>(`/projects/${projectId}`)
      .then((loaded) => !cancelled && setProject(loaded))
      .catch((error) => {
        if (!cancelled) {
          setProjectError(isApiError(error) && error.status === 404 ? "missing" : "unavailable");
        }
      });
    apiGet<SyncStatus>(`/projects/${projectId}/sync`)
      .then((loaded) => !cancelled && setStatus(loaded))
      .catch(() => {
        // 수집 상태를 못 읽는다고 문서를 못 읽는 것은 아니다. 배너만 생략한다.
      });
    return () => {
      cancelled = true;
    };
  }, [projectId]);

  if (projectError === "missing") {
    return <DocumentMissing projectId={projectId ?? ""} />;
  }
  if (projectError === "unavailable") {
    return <AccessUnavailable />;
  }
  if (!project || list.state === "loading") {
    return <main className="centered">불러오는 중입니다.</main>;
  }
  if (list.state === "gone") {
    return <SnapshotGone projectId={project.id} />;
  }
  if (list.state === "missing") {
    return <DocumentMissing projectId={project.id} />;
  }
  if (list.state === "unavailable") {
    return <AccessUnavailable />;
  }

  const items = list.list.items;
  const current = documentId ? items.find((item) => item.id === documentId) : undefined;

  return (
    <AppShell
      project={project}
      status={status}
      documents={items}
      currentDocumentId={documentId}
      currentPath={current?.path}
      checklistErrors={checklist.state === "ready" ? errorCountOf(checklist.checklist) : undefined}
    >
      {status && <StaleBanner status={status} />}
      {body()}
    </AppShell>
  );

  function body() {
    // 첫 수집 전이다. 빈 목록을 문서 없음으로 보여주지 않는다.
    if (list.state === "ready" && list.list.snapshotId === null) {
      return <FirstSyncWaiting />;
    }
    if (!documentId) {
      return (
        <section className="state">
          <h1>{project?.fullName}</h1>
          <p>왼쪽 목록에서 문서를 선택하세요.</p>
        </section>
      );
    }
    switch (document.state) {
      case "loading":
        return <p className="centered">문서를 불러오는 중입니다.</p>;
      case "waiting":
        return <FirstSyncWaiting />;
      case "gone":
        return <SnapshotGone projectId={project!.id} />;
      case "missing":
        return <DocumentMissing projectId={project!.id} />;
      case "unreadable":
        return <DocumentUnreadable projectId={project!.id} />;
      case "unavailable":
        return <AccessUnavailable />;
      case "ready":
        return <Body />;
    }
  }

  function Body() {
    if (document.state !== "ready") {
      return null;
    }
    const view = document.document;
    return (
      <article className="doc-shell">
        <div className="doc">
          <h1>{view.title}</h1>
          <div className="dmeta">
            <span>{view.path}</span>
            <span>{view.sourceRevision.slice(0, 10)}</span>
            <span>{view.snapshotId.slice(0, 8)}</span>
          </div>
          <DocumentBody html={view.html} diagrams={view.diagrams} title={view.title} />
        </div>
        <aside className="toc">
          <div className="h">목차</div>
          {view.headings
            .filter((heading) => heading.level <= 3)
            .map((heading) => (
              <a key={heading.id} href={`#${heading.id}`} data-level={heading.level}>
                {heading.text}
              </a>
            ))}
          {view.warnings.length > 0 && (
            <div className="wz">
              <div className="h">경고 {view.warnings.length}</div>
              {view.warnings.map((warning, index) => (
                <p key={`${warning.code}-${index}`}>
                  <span className="mono">{warning.code}</span>
                  {warning.detail}
                </p>
              ))}
            </div>
          )}
        </aside>
      </article>
    );
  }
}
