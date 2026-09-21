import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { apiGet, isApiError } from "../../shared/api/client";
import { AccessUnavailable, DocumentMissing } from "../documents/DocumentStates";
import { useDocumentList } from "../documents/useDocument";
import type { ProjectItem } from "../projects/types";
import AppShell from "../shell/AppShell";
import { FirstSyncWaiting } from "../sync/SyncStates";
import type { SearchResults } from "./types";

const MAX_QUERY = 200;

/**
 * UI-004 검색.
 *
 * <p>대상은 현재 프로젝트의 한 게시본뿐이다. 결과가 없을 때와 아직 수집하지 않았을 때를 구분해
 * 보여준다 — 둘을 같은 문구로 쓰면 방금 연결한 저장소가 빈 저장소처럼 보인다.
 */
export default function SearchPage() {
  const { projectId } = useParams();
  const [params, setParams] = useSearchParams();
  const query = params.get("q") ?? "";

  const [project, setProject] = useState<ProjectItem | null>(null);
  const [results, setResults] = useState<SearchResults | null>(null);
  const [failure, setFailure] = useState<"missing" | "unavailable" | undefined>();
  const [input, setInput] = useState(query);
  const { list } = useDocumentList(projectId);

  useEffect(() => {
    if (!projectId) {
      return;
    }
    apiGet<ProjectItem>(`/projects/${projectId}`)
      .then(setProject)
      .catch((error) =>
        setFailure(isApiError(error) && error.status === 404 ? "missing" : "unavailable"),
      );
  }, [projectId]);

  useEffect(() => {
    if (!projectId || !query) {
      setResults(null);
      return;
    }
    let cancelled = false;
    apiGet<SearchResults>(`/projects/${projectId}/search?q=${encodeURIComponent(query)}`)
      .then((loaded) => !cancelled && setResults(loaded))
      .catch(() => !cancelled && setResults(null));
    return () => {
      cancelled = true;
    };
  }, [projectId, query]);

  if (failure === "missing") {
    return <DocumentMissing projectId={projectId ?? ""} />;
  }
  if (failure === "unavailable") {
    return <AccessUnavailable />;
  }
  if (!project) {
    return <main className="centered">불러오는 중입니다.</main>;
  }

  const documents = list.state === "ready" ? list.list.items : [];
  const notCollected = list.state === "ready" && list.list.snapshotId === null;
  const tooLong = input.length > MAX_QUERY;

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (tooLong) {
      return;
    }
    setParams(input ? { q: input } : {});
  };

  return (
    <AppShell project={project} documents={documents} active="search">
      {notCollected ? (
        <FirstSyncWaiting />
      ) : (
        <section className="search">
          <form className="sfield" onSubmit={submit}>
            <label htmlFor="q">검색어</label>
            <input
              id="q"
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="문서 제목과 본문에서 찾습니다"
            />
            <button type="submit" className="button">
              찾기
            </button>
            <span className="n">현재 프로젝트 · {results ? `${results.total}건` : "—"}</span>
          </form>
          {tooLong && (
            <p role="alert" className="n">
              검색어는 {MAX_QUERY}자까지입니다. 지금 {input.length}자입니다.
            </p>
          )}

          {results && results.items.length === 0 && (
            <section className="state">
              <h1>일치하는 문서가 없습니다</h1>
              <p>검색어를 줄이거나 문서 목록에서 찾아보세요.</p>
              <p className="n">권한이 없는 문서는 결과에 나타나지 않습니다.</p>
              <Link className="button" to={`/projects/${project.id}`}>
                문서 목록 열기
              </Link>
            </section>
          )}

          <ul className="hits">
            {results?.items.map((hit) => (
              <li className="hit" key={hit.documentId}>
                <Link
                  className="t"
                  to={`/projects/${project.id}/documents/${hit.documentId}${
                    hit.anchor ? `#${hit.anchor}` : ""
                  }`}
                >
                  {hit.title}
                </Link>
                <span className="mono">
                  {hit.path}
                  {hit.anchor ? ` · #${hit.anchor}` : ""}
                </span>
                <p className="x">{hit.excerpt}</p>
              </li>
            ))}
          </ul>
        </section>
      )}
    </AppShell>
  );
}
