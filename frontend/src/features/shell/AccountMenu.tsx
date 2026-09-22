import { useState } from "react";
import { apiPost } from "../../shared/api/client";
import { forgetSession } from "../auth/useSession";

/**
 * UI-000 상단바의 계정 표식과 로그아웃.
 *
 * <p>로그아웃은 API-004를 불러 서버의 세션을 실제로 끊는다. 화면만 로그인 화면으로 옮기면
 * 쿠키가 살아 있어 주소만 바꾸면 다시 들어올 수 있다.
 *
 * <p>끝난 뒤에는 페이지를 새로 연다. 화면이 기억하고 있던 세션·문서·판정을 모두 버려야
 * 다음 사람이 앞사람의 자료를 보지 않는다.
 */
export default function AccountMenu({ login, csrfToken }: { login: string; csrfToken: string }) {
  const [leaving, setLeaving] = useState(false);

  const signOut = async () => {
    if (leaving) {
      return;
    }
    setLeaving(true);
    try {
      await apiPost("/logout", {}, csrfToken);
    } catch {
      // 이미 끊긴 세션일 수 있다. 어느 쪽이든 로그인 화면으로 보낸다.
    } finally {
      forgetSession();
      window.location.assign("/login");
    }
  };

  return (
    <span className="account">
      <span className="who" title="로그인한 계정">
        {login}
      </span>
      <button type="button" className="button button--quiet" onClick={signOut} disabled={leaving}>
        로그아웃
      </button>
    </span>
  );
}
