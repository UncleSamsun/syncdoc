import { useEffect, useState } from "react";
import { apiGet, isApiError } from "../../shared/api/client";
import type { ChecklistView } from "./types";

/**
 * 화면이 구분해야 하는 상태. `waiting`은 첫 수집 전(UI-007), `gone`은 회수된 게시본(UI-012),
 * `missing`은 없거나 볼 수 없는 프로젝트(UI-011)다.
 */
export type ChecklistState =
  | { state: "loading" }
  | { state: "ready"; checklist: ChecklistView }
  | { state: "waiting" }
  | { state: "missing" }
  | { state: "gone" }
  | { state: "unavailable" };

/**
 * API-025. 사이드바 건수도 이 값을 쓴다.
 *
 * <p>같은 게시본의 판정은 바뀌지 않으므로 한 번 읽은 것을 프로젝트별로 기억한다. 화면을 옮길
 * 때마다 다시 받으면 오류가 많은 저장소에서 목록을 되풀이해 내려받게 된다.
 */
const cache = new Map<string, ChecklistView>();

export function useChecklist(projectId: string | undefined, reloadKey = 0): ChecklistState {
  const [state, setState] = useState<ChecklistState>(() => {
    const remembered = projectId ? cache.get(projectId) : undefined;
    return remembered ? { state: "ready", checklist: remembered } : { state: "loading" };
  });

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let cancelled = false;
    const remembered = cache.get(projectId);
    if (remembered) {
      setState({ state: "ready", checklist: remembered });
    }
    if (reloadKey > 0) {
      // 새 게시본이 나왔다. 기억한 판정은 이전 게시본의 것이다.
      cache.delete(projectId);
    }
    apiGet<ChecklistView>(`/projects/${projectId}/spec-checklist`)
      .then((checklist) => {
        cache.set(projectId, checklist);
        if (!cancelled) {
          setState({ state: "ready", checklist });
        }
      })
      .catch((error) => {
        if (!cancelled && !remembered) {
          setState({ state: statusOf(error) });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [projectId, reloadKey]);

  return state;
}

/** 수집이 끝나 새 게시본이 생기면 기억한 값을 버린다. */
export function forgetChecklist(projectId: string): void {
  cache.delete(projectId);
}

function statusOf(error: unknown): "waiting" | "missing" | "gone" | "unavailable" {
  if (!isApiError(error)) {
    return "unavailable";
  }
  switch (error.status) {
    case 409:
      return "waiting";
    case 410:
      return "gone";
    case 404:
      return "missing";
    default:
      return "unavailable";
  }
}
