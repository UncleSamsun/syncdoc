import { useEffect, useState } from "react";
import { apiGet } from "../../shared/api/client";

export type Me = {
  id: string;
  githubUserId: string;
  login: string;
  serviceAdmin: boolean;
  csrfToken: string;
};

export type SessionState = { state: "loading" } | { state: "anonymous" } | { state: "signed-in"; me: Me };

/**
 * 한 번 확인한 세션을 기억한다. 셸이 계정 표식을 그리려면 어느 화면에서도 필요한데,
 * 화면을 옮길 때마다 다시 물으면 같은 답을 되풀이해 받는다.
 */
let remembered: Me | null = null;

/** API-003으로 세션을 확인한다. 401은 오류가 아니라 로그인하지 않은 상태다. */
export function useSession(): SessionState {
  const [session, setSession] = useState<SessionState>(
    remembered ? { state: "signed-in", me: remembered } : { state: "loading" },
  );

  useEffect(() => {
    let cancelled = false;
    apiGet<Me>("/me")
      .then((me) => {
        remembered = me;
        if (!cancelled) {
          setSession({ state: "signed-in", me });
        }
      })
      .catch(() => {
        remembered = null;
        if (!cancelled) {
          setSession({ state: "anonymous" });
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return session;
}

/** 로그아웃한 뒤에는 기억한 세션을 버린다. */
export function forgetSession(): void {
  remembered = null;
}
