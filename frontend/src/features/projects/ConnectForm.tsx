import { useEffect, useState } from "react";
import { apiGet, isApiError } from "../../shared/api/client";
import { DEFAULT_BRANCH, DEFAULT_DOCS_ROOT } from "./types";
import type { BranchItem, RepositoryItem } from "./types";

type Props = {
  repositories: RepositoryItem[];
  onConnect: (input: { githubRepositoryId: string; branch: string; docsRoot: string }) => Promise<void>;
  fieldError?: { field: string; message: string };
};

/**
 * UI-001의 연결 양식. 저장소는 API-008 결과에서만 고르고 브랜치는 API-024 결과에서만 고른다.
 * 저장소 주소나 경로를 직접 입력하는 칸을 두지 않는다.
 */
export default function ConnectForm({ repositories, onConnect, fieldError }: Props) {
  const [githubRepositoryId, setGithubRepositoryId] = useState("");
  const [branch, setBranch] = useState(DEFAULT_BRANCH);
  const [docsRoot, setDocsRoot] = useState(DEFAULT_DOCS_ROOT);
  const [branches, setBranches] = useState<BranchItem[]>([]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!githubRepositoryId) {
      setBranches([]);
      return;
    }
    let cancelled = false;
    apiGet<{ items: BranchItem[] }>(`/github/repositories/${githubRepositoryId}/branches`)
      .then((response) => {
        if (cancelled) return;
        setBranches(response.items);
        // 기본값은 main이다. 없으면 저장소 기본 브랜치를 쓴다.
        const hasMain = response.items.some((item) => item.name === DEFAULT_BRANCH);
        setBranch(hasMain ? DEFAULT_BRANCH : (response.items.find((i) => i.isDefault)?.name ?? ""));
      })
      .catch(() => !cancelled && setBranches([]));
    return () => {
      cancelled = true;
    };
  }, [githubRepositoryId]);

  if (repositories.length === 0) {
    return (
      <section>
        <p>연결할 수 있는 저장소가 없습니다</p>
        <p>앱과 계정 양쪽에서 접근할 수 있는 저장소만 연결할 수 있습니다.</p>
      </section>
    );
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!githubRepositoryId || submitting) return;
    setSubmitting(true);
    try {
      await onConnect({ githubRepositoryId, branch, docsRoot });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={submit}>
      <label htmlFor="repository">저장소</label>
      <select
        id="repository"
        value={githubRepositoryId}
        onChange={(event) => setGithubRepositoryId(event.target.value)}
      >
        <option value="">저장소를 고르세요</option>
        {repositories.map((repository) => (
          <option key={repository.githubRepositoryId} value={repository.githubRepositoryId}>
            {repository.fullName}
          </option>
        ))}
      </select>

      <label htmlFor="branch">기준 브랜치</label>
      <select id="branch" value={branch} onChange={(event) => setBranch(event.target.value)}>
        {branches.length === 0 && <option value={branch}>{branch}</option>}
        {branches.map((item) => (
          <option key={item.name} value={item.name}>
            {item.name}
          </option>
        ))}
      </select>
      {fieldError?.field === "branch" && <p role="alert">{fieldError.message}</p>}
      <p>연결한 뒤 상단바에서 바꿀 수 있습니다.</p>

      <label htmlFor="docsRoot">문서 경로</label>
      <input id="docsRoot" value={docsRoot} onChange={(event) => setDocsRoot(event.target.value)} />
      {fieldError?.field === "docsRoot" && <p role="alert">{fieldError.message}</p>}

      <p>GitHub Project를 연결하지 않으면 문서와 Issue·PR 현황만 제공합니다.</p>

      <button type="submit" disabled={!githubRepositoryId || submitting}>
        연결
      </button>
    </form>
  );
}

export function fieldErrorOf(error: unknown): { field: string; message: string } | undefined {
  if (isApiError(error) && error.status === 422) {
    const field = typeof error.details.field === "string" ? error.details.field : "docsRoot";
    return { field, message: error.message };
  }
  return undefined;
}
