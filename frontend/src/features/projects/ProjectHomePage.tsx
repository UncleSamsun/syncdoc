import { useCallback, useEffect, useState } from "react";
import { apiGet, apiPost, isApiError } from "../../shared/api/client";
import { formatMoment, syncLabelOf, syncToneOf } from "../sync/syncLabels";
import ConnectForm, { fieldErrorOf } from "./ConnectForm";
import type { ProjectItem, RepositoryItem } from "./types";

type Props = { csrfToken: string };

/**
 * UI-001 프로젝트 홈. 사이드바를 두지 않는다. 아직 프로젝트 맥락이 없다.
 * 중복 연결(409)은 오류로 보이지 않게 하고 그 항목을 이미 연결됨으로 표시한다.
 */
export default function ProjectHomePage({ csrfToken }: Props) {
  const [projects, setProjects] = useState<ProjectItem[]>([]);
  const [repositories, setRepositories] = useState<RepositoryItem[]>([]);
  const [loadError, setLoadError] = useState<string>();
  const [fieldError, setFieldError] = useState<{ field: string; message: string }>();
  const [alreadyConnectedId, setAlreadyConnectedId] = useState<string>();
  // 연결 양식은 주 버튼의 펼친 상태다. 연결한 프로젝트가 없으면 바로 펼쳐 둔다.
  const [formOpen, setFormOpen] = useState(true);

  const reload = useCallback(async () => {
    try {
      const [projectList, repositoryList] = await Promise.all([
        apiGet<{ items: ProjectItem[] }>("/projects"),
        apiGet<{ items: RepositoryItem[]; complete: boolean }>("/github/repositories"),
      ]);
      setProjects(projectList.items);
      setRepositories(repositoryList.items);
      setLoadError(undefined);
    } catch (error) {
      setLoadError(
        isApiError(error) && error.status === 503
          ? "GitHub 권한을 확인할 수 없습니다."
          : "목록을 불러오지 못했습니다.",
      );
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const connect = async (input: { githubRepositoryId: string; branch: string; docsRoot: string }) => {
    setFieldError(undefined);
    setAlreadyConnectedId(undefined);
    try {
      await apiPost<ProjectItem>("/projects", input, csrfToken);
      setFormOpen(false);
      await reload();
    } catch (error) {
      if (isApiError(error) && error.status === 409) {
        // 중복은 오류가 아니다. 이미 열람할 수 있는 프로젝트를 알려 준다.
        const projectId = typeof error.details.projectId === "string" ? error.details.projectId : undefined;
        setAlreadyConnectedId(projectId);
        await reload();
        return;
      }
      const field = fieldErrorOf(error);
      if (field) {
        setFieldError(field);
        return;
      }
      setLoadError("연결하지 못했습니다.");
    }
  };

  return (
    <main className="home">
      <header className="home-h">
        <h1>프로젝트 홈</h1>
        <button type="button" className="button" onClick={() => setFormOpen((open) => !open)}>
          저장소 연결
        </button>
      </header>
      <p className="lead">연결한 저장소의 문서와 실행 상태를 한곳에서 봅니다.</p>
      {loadError && (
        <p className="banner banner--fail" role="alert">
          {loadError}
        </p>
      )}

      <ul className="plist">
        {projects.map((project) => (
          <li className="pcard" key={project.id}>
            <span className={`st st--${syncToneOf(project.syncState)}`}>
              <span className="dot" aria-hidden="true" />
              {syncLabelOf(project)}
            </span>
            <span className="nm">
              <b>{project.fullName}</b>
              <span className="mono">
                {project.branch} · {project.docsRoot} ·{" "}
                {project.currentSnapshotId?.slice(0, 8) ?? "snapshot 없음"} ·{" "}
                {formatMoment(project.lastSuccessAt) ?? "성공한 수집 없음"}
              </span>
            </span>
            <span className="rr">
              {project.documentCount !== null && (
                <span className="chip">문서 {project.documentCount}건</span>
              )}
              {project.syncState === "failed" && project.syncErrorCode && (
                <span className="chip chip--fail">오류 {project.syncErrorCode}</span>
              )}
              {alreadyConnectedId === project.id && <span className="chip">이미 연결됨</span>}
              <a className="button button--quiet" href={`/projects/${project.id}`}>
                열기
              </a>
            </span>
          </li>
        ))}
      </ul>

      {formOpen && (
        <section className="connect">
          <h2>저장소 연결</h2>
          <ConnectForm repositories={repositories} onConnect={connect} fieldError={fieldError} />
        </section>
      )}
    </main>
  );
}
