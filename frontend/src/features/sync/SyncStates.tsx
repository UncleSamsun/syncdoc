import type { SyncStatus } from "../projects/types";
import { formatMoment } from "./syncLabels";

/**
 * UI-007 첫 동기화 대기.
 *
 * <p>빈 목록을 `문서가 없습니다`로 표시하지 않는다. 대기와 없음에 같은 문구를 쓰면 방금 연결한
 * 저장소가 빈 저장소처럼 보인다.
 */
export function FirstSyncWaiting() {
  return (
    <section className="state">
      <h1>첫 수집을 기다리는 중</h1>
      <p>저장소를 연결했습니다. 문서 목록은 첫 수집이 끝나면 나타납니다.</p>
      <div className="skeleton" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
    </section>
  );
}

/**
 * UI-008 갱신 실패 · 오래된 정보.
 *
 * <p>마지막 정상 데이터를 계속 보여주되 최신이라고 쓰지 않는다. 오류 문구에 토큰이나 서버 내부
 * 경로를 넣지 않으며, 서버가 준 오류 코드만 그대로 보여준다.
 */
export function StaleBanner({ status }: { status: SyncStatus }) {
  if (status.state !== "failed") {
    return null;
  }
  const basis = formatMoment(status.lastSuccessAt);
  const retry = formatMoment(status.nextRetryAt);

  return (
    <div className="banner banner--fail" role="status">
      <div className="banner-h">
        <strong>
          {basis ? `${basis} 기준 정보입니다 · 이후 갱신 실패` : "아직 성공한 수집이 없습니다"}
        </strong>
        {retry && <span className="mono">재시도 {retry}</span>}
      </div>
      <p>
        {basis
          ? "아래 내용은 마지막으로 성공한 snapshot입니다. 실패한 수집이 이 데이터를 덮어쓰지 않았습니다."
          : "첫 수집이 실패했습니다. 문서 목록은 수집이 성공하면 나타납니다."}
      </p>
      {status.errorCode && <p className="mono">{status.errorCode}</p>}
    </div>
  );
}
