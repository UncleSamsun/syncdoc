import { useCallback, useEffect, useState } from "react";
import { apiGet, apiPost, isApiError } from "../../shared/api/client";
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
    <main>
      <h1>프로젝트 홈</h1>
      <p>연결한 저장소의 문서와 실행 상태를 한곳에서 봅니다.</p>
      {loadError && <p role="alert">{loadError}</p>}

      <ul>
        {projects.map((project) => (
          <li key={project.id}>
            <span>{project.fullName}</span>
            <span>
              {project.branch} · {project.docsRoot} ·{" "}
              {project.currentSnapshotId ?? "snapshot 없음"}
            </span>
            <span>{project.syncState === "queued" ? "첫 수집 대기" : "최신"}</span>
            {alreadyConnectedId === project.id && <span>이미 연결됨</span>}
            <a href={`/projects/${project.id}`}>열기</a>
          </li>
        ))}
      </ul>

      <h2>저장소 연결</h2>
      <ConnectForm repositories={repositories} onConnect={connect} fieldError={fieldError} />
    </main>
  );
}
