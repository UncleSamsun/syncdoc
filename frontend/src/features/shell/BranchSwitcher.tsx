import { useState } from "react";
import { apiGet, apiPatch, isApiError } from "../../shared/api/client";
import type { ProjectItem } from "../projects/types";

type Branch = { name: string; isDefault: boolean };

/**
 * UI-000의 브랜치 전환.
 *
 * <p>읽을 수 있는 브랜치만 나열한다(API-024). 이름을 직접 입력하는 수단을 두지 않는다 — 없는
 * 브랜치를 적어 넣으면 연결 설정이 가리키는 곳이 사라진다.
 *
 * <p>바꾸면 API-012에 `expectedVersion`과 CSRF 토큰을 함께 보낸다. 서버가 새 수집을 예약하고,
 * 끝날 때까지 이전 브랜치의 게시본을 계속 보여준다. 두 브랜치의 문서를 섞지 않기 위해서다.
 *
 * <p>연결자나 서비스 관리자가 아니면 목록을 열지 않고 지금 브랜치만 보여준다.
 */
export default function BranchSwitcher({ project, csrfToken }:
  { project: ProjectItem; csrfToken: string }) {
  const [branches, setBranches] = useState<Branch[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string>();

  if (!project.manageable) {
    return (
      <span className="sw sw--br sw--locked" title="연결자 또는 서비스 관리자만 바꿀 수 있습니다">
        {project.branch}
      </span>
    );
  }

  // 목록은 열 때 한 번만 받는다. 화면마다 미리 받아 두면 쓰지도 않을 요청이 늘어난다.
  const load = async () => {
    if (branches || busy) {
      return;
    }
    try {
      const listed = await apiGet<{ items: Branch[] }>(
        `/github/repositories/${project.githubRepositoryId}/branches`,
      );
      setBranches(listed.items);
    } catch {
      setMessage("브랜치 목록을 불러오지 못했습니다.");
    }
  };

  const change = async (branch: string) => {
    if (branch === project.branch) {
      return;
    }
    setBusy(true);
    setMessage(undefined);
    try {
      await apiPatch(`/projects/${project.id}`, { branch, expectedVersion: project.version },
        csrfToken);
      // 서버가 새 수집을 예약했다. 화면 전체가 같은 설정을 보도록 다시 연다.
      window.location.reload();
    } catch (error) {
      setBusy(false);
      if (isApiError(error) && error.status === 409) {
        setMessage("다른 사용자가 연결 설정을 먼저 바꿨습니다. 최신 설정을 불러옵니다.");
        window.setTimeout(() => window.location.reload(), 1500);
        return;
      }
      setMessage(isApiError(error) ? error.message : "브랜치를 바꾸지 못했습니다.");
    }
  };

  return (
    <span className="sw sw--br">
      <select
        aria-label="기준 브랜치"
        value={project.branch}
        disabled={busy}
        onMouseDown={load}
        onFocus={load}
        onChange={(event) => change(event.target.value)}
      >
        {/* 목록을 받기 전에도 지금 브랜치는 보여야 한다. 빈 칸으로 두지 않는다. */}
        {(branches ?? [{ name: project.branch, isDefault: false }]).map((branch) => (
          <option key={branch.name} value={branch.name}>
            {branch.name}
          </option>
        ))}
      </select>
      {message && (
        <span className="n" role="alert">
          {message}
        </span>
      )}
    </span>
  );
}
