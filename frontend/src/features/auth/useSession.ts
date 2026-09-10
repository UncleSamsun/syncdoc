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

/** API-003으로 세션을 확인한다. 401은 오류가 아니라 로그인하지 않은 상태다. */
export function useSession(): SessionState {
  const [session, setSession] = useState<SessionState>({ state: "loading" });

  useEffect(() => {
    let cancelled = false;
    apiGet<Me>("/me")
      .then((me) => !cancelled && setSession({ state: "signed-in", me }))
      .catch(() => !cancelled && setSession({ state: "anonymous" }));
    return () => {
      cancelled = true;
    };
  }, []);

  return session;
}
