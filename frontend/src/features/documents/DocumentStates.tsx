import { Link } from "react-router-dom";

/**
 * UI-011 없는 문서 · 권한 없음.
 *
 * <p>권한 없는 비공개 문서와 존재하지 않는 문서에 같은 문구를 쓴다. 두 경우를 구분할 수 있는
 * 표시를 만들지 않는다 — 문구가 다르면 그 문서가 있다는 사실이 새어 나간다.
 */
export function DocumentMissing({ projectId }: { projectId: string }) {
  return (
    <section className="state">
      <h1>이 문서를 찾을 수 없습니다</h1>
      <p>경로가 바뀌었거나 접근 권한이 없습니다. 문서 목록에서 다시 찾아보세요.</p>
      <Link className="button" to={`/projects/${projectId}`}>
        문서 목록 열기
      </Link>
    </section>
  );
}

/**
 * UI-012 회수된 snapshot.
 *
 * <p>막다른 길을 만들지 않는다. 이전 snapshot 주소로 들어와도 현재 게시본으로 갈 수 있게 한다.
 */
export function SnapshotGone({ projectId }: { projectId: string }) {
  return (
    <section className="state">
      <div className="banner banner--fail" role="status">
        <strong>이 버전은 더 이상 제공되지 않습니다</strong>
      </div>
      <p>링크가 가리키는 snapshot이 회수되었습니다. 현재 게시본으로 이동할 수 있습니다.</p>
      <Link className="button" to={`/projects/${projectId}`}>
        최신 문서 목록으로
      </Link>
    </section>
  );
}

/** 변환 결과가 없어 본문을 보여줄 수 없는 문서다(422). */
export function DocumentUnreadable({ projectId }: { projectId: string }) {
  return (
    <section className="state">
      <h1>이 문서를 표시할 수 없습니다</h1>
      <p>문서를 변환하지 못했습니다. 다음 수집에서 다시 시도합니다.</p>
      <Link className="button" to={`/projects/${projectId}`}>
        문서 목록 열기
      </Link>
    </section>
  );
}

/** GitHub 권한을 확인할 수 없는 상태다(503). 예전에 볼 수 있었다는 이유로 내용을 보여주지 않는다. */
export function AccessUnavailable() {
  return (
    <section className="state">
      <h1>지금은 확인할 수 없습니다</h1>
      <p>GitHub 권한을 확인할 수 없습니다. 잠시 뒤 다시 시도해 주세요.</p>
    </section>
  );
}
