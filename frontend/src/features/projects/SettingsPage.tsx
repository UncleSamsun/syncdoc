import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { apiDelete, apiGet, apiPatch, isApiError } from "../../shared/api/client";
import { useSession } from "../auth/useSession";
import { AccessUnavailable, DocumentMissing } from "../documents/DocumentStates";
import { useDocumentList } from "../documents/useDocument";
import AppShell from "../shell/AppShell";
import { errorCountOf } from "../spec/types";
import { useChecklist } from "../spec/useChecklist";
import { fieldErrorOf } from "./ConnectForm";
import type { ProjectItem } from "./types";

/**
 * UI-015 연결 설정.
 *
 * <p>문서 경로와 GitHub Project 연결을 바꾼다. 기준 브랜치는 상단바가 맡으므로 여기서 또 두지
 * 않는다 — 같은 값을 두 곳에서 바꾸게 하면 어느 쪽이 방금 쓴 값인지 알 수 없다.
 *
 * <p>저장소 연결을 끊는 수단을 두지 않는다. 그 계약이 없고, 게시본·작업·Issue 사본을 어떻게
 * 할지 정하지 않은 채로 끊는 단추만 두면 되돌릴 수 없는 일이 된다.
 */
export default function SettingsPage() {
  const { projectId } = useParams();
  const session = useSession();
  const [project, setProject] = useState<ProjectItem | null>(null);
  const [failure, setFailure] = useState<"missing" | "unavailable" | undefined>();
  const [docsRoot, setDocsRoot] = useState("");
  const [projectNodeId, setProjectNodeId] = useState("");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [fieldError, setFieldError] = useState<{ field: string; message: string }>();
  const [conflict, setConflict] = useState(false);
  const [confirmName, setConfirmName] = useState("");
  const [disconnecting, setDisconnecting] = useState(false);
  const [disconnectError, setDisconnectError] = useState<string>();
  const { list } = useDocumentList(projectId);
  const checklist = useChecklist(projectId);

  const load = useCallback(() => {
    if (!projectId) {
      return;
    }
    apiGet<ProjectItem>(`/projects/${projectId}`)
      .then((loaded) => {
        setProject(loaded);
        setDocsRoot(loaded.docsRoot);
        setProjectNodeId(loaded.githubProjectNodeId ?? "");
      })
      .catch((error) =>
        setFailure(isApiError(error) && error.status === 404 ? "missing" : "unavailable"),
      );
  }, [projectId]);

  useEffect(load, [load]);

  if (failure === "missing") {
    return <DocumentMissing projectId={projectId ?? ""} />;
  }
  if (failure === "unavailable") {
    return <AccessUnavailable />;
  }
  if (!project) {
    return <main className="centered">불러오는 중입니다.</main>;
  }

  const csrfToken = session.state === "signed-in" ? session.me.csrfToken : "";
  const documents = list.state === "ready" ? list.list.items : [];

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!project.manageable || saving) {
      return;
    }
    setSaving(true);
    setFieldError(undefined);
    setConflict(false);
    setSaved(false);
    try {
      await apiPatch(`/projects/${project.id}`, {
        docsRoot,
        githubProjectNodeId: projectNodeId,
        expectedVersion: project.version,
      }, csrfToken);
      setSaved(true);
      // 바뀐 값을 다시 읽어 보여준다. 서버가 다듬은 경로를 화면이 모른 채 두지 않는다.
      load();
    } catch (error) {
      if (isApiError(error) && error.status === 409) {
        // 쓰던 값을 덮어써 보내지 않는다. 최신 설정을 읽어 다시 시작한다.
        setConflict(true);
        load();
        return;
      }
      const field = fieldErrorOf(error);
      setFieldError(field ?? { field: "docsRoot", message: "저장하지 못했습니다." });
    } finally {
      setSaving(false);
    }
  };

  const disconnect = async () => {
    if (!project.manageable || disconnecting) {
      return;
    }
    setDisconnecting(true);
    setDisconnectError(undefined);
    try {
      await apiDelete(`/projects/${project.id}`, { fullName: confirmName }, csrfToken);
      // 없는 프로젝트의 주소에 남겨 두지 않는다.
      window.location.assign("/");
    } catch (error) {
      setDisconnecting(false);
      setDisconnectError(isApiError(error) ? error.message : "연결을 끊지 못했습니다.");
    }
  };

  return (
    <AppShell
      project={project}
      documents={documents}
      checklistErrors={checklist.state === "ready" ? errorCountOf(checklist.checklist) : undefined}
    >
      <section className="settings">
        <header className="chk-h">
          <h1>연결 설정</h1>
          <span className="mono">{project.fullName}</span>
        </header>

        <dl className="fixed">
          <dt>저장소</dt>
          <dd>
            {project.fullName}
            <span className="n"> 연결한 저장소는 바꾸지 않습니다. 다른 저장소는 새로 연결합니다.</span>
          </dd>
          <dt>기준 브랜치</dt>
          <dd>
            {project.branch}
            <span className="n"> 상단바에서 바꿉니다.</span>
          </dd>
        </dl>

        {!project.manageable && (
          <p className="n" role="status">연결한 사람 또는 서비스 관리자만 바꿀 수 있습니다.</p>
        )}
        {conflict && (
          <p className="banner banner--fail" role="alert">
            다른 사용자가 연결 설정을 먼저 바꿨습니다. 최신 설정을 불러옵니다.
          </p>
        )}

        <form className="sform" onSubmit={save}>
          <label htmlFor="docsRoot">문서 경로</label>
          <input
            id="docsRoot"
            value={docsRoot}
            readOnly={!project.manageable}
            onChange={(event) => setDocsRoot(event.target.value)}
          />
          {fieldError?.field === "docsRoot" && (
            <p className="n" role="alert">{fieldError.message}</p>
          )}
          <p className="n">바꾸면 새 수집이 돕니다. 끝날 때까지 지금 게시본을 계속 보여줍니다.</p>

          <label htmlFor="githubProjectNodeId">GitHub Project</label>
          <input
            id="githubProjectNodeId"
            value={projectNodeId}
            readOnly={!project.manageable}
            placeholder="연결하지 않음"
            onChange={(event) => setProjectNodeId(event.target.value)}
          />
          {fieldError?.field === "githubProjectNodeId" && (
            <p className="n" role="alert">{fieldError.message}</p>
          )}
          <p className="n">
            연결하지 않으면 문서와 Issue·PR 현황만 제공합니다. 칸을 비우면 연결을 해제합니다.
          </p>

          {project.manageable && (
            <button type="submit" className="button" disabled={saving}>
              저장
            </button>
          )}
          {saved && <span className="n" role="status">저장했습니다</span>}
        </form>

        {project.manageable && (
          <section className="danger">
            <h2>연결 해제</h2>
            <p className="n">
              수집한 문서·첨부·작업·Issue 사본을 모두 지웁니다. GitHub 저장소는 건드리지 않습니다.
            </p>
            <label htmlFor="confirmName">
              끊으려면 <span className="mono">{project.fullName}</span>을 그대로 입력하세요
            </label>
            <input
              id="confirmName"
              value={confirmName}
              onChange={(event) => setConfirmName(event.target.value)}
            />
            {disconnectError && <p className="n" role="alert">{disconnectError}</p>}
            <button
              type="button"
              className="button button--danger"
              /* 이름이 맞기 전에는 누를 수 없다. 되돌릴 수 없는 일을 단추 하나로 만들지 않는다. */
              disabled={confirmName !== project.fullName || disconnecting}
              onClick={disconnect}
            >
              연결 끊기
            </button>
          </section>
        )}
      </section>
    </AppShell>
  );
}
