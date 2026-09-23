import { useEffect, useRef } from "react";
import { apiGet } from "../../shared/api/client";
import type { SyncStatus } from "../projects/types";

/** UI-000이 정한 확인 간격. 갱신을 놓치지 않으면서 저장소를 자주 두드리지 않는 값이다. */
export const WATCH_INTERVAL_MS = 20_000;

/**
 * 화면을 보는 동안 새 게시본이 나왔는지 지켜본다(API-014).
 *
 * <p>`lastSuccessAt`이 아니라 `snapshotId`가 바뀌는 것을 본다. 바뀐 것이 없어 다시 게시하지
 * 않은 수집도 성공 시각은 갱신하므로, 그것만 보면 없는 새 버전을 알리게 된다.
 *
 * <p>무엇을 할지는 부르는 화면이 정한다. 목록은 조용히 다시 읽고, 문서 본문은 읽던 자리를
 * 잃지 않게 배너만 띄운다.
 */
export function useSnapshotWatch(projectId: string | undefined,
                                 onNewSnapshot: (snapshotId: string) => void): void {
  const known = useRef<string | null>(null);
  const notify = useRef(onNewSnapshot);
  notify.current = onNewSnapshot;

  useEffect(() => {
    if (!projectId) {
      return;
    }
    known.current = null;
    let stopped = false;

    const check = async () => {
      try {
        const status = await apiGet<SyncStatus>(`/projects/${projectId}/sync`);
        if (stopped || !status.snapshotId) {
          return;
        }
        if (known.current === null) {
          known.current = status.snapshotId;
          return;
        }
        if (known.current !== status.snapshotId) {
          known.current = status.snapshotId;
          notify.current(status.snapshotId);
        }
      } catch {
        // 한 번 못 읽었다고 알릴 것은 없다. 다음 차례에 다시 본다.
      }
    };

    void check();
    const timer = window.setInterval(check, WATCH_INTERVAL_MS);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [projectId]);
}
