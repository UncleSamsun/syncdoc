import LoginPage from "../features/auth/LoginPage";
import UninvitedPage from "../features/auth/UninvitedPage";
import { useSession } from "../features/auth/useSession";
import ProjectHomePage from "../features/projects/ProjectHomePage";

/**
 * 세션이 없으면 어느 경로로 들어와도 로그인 화면이다. UI-005의 검증 항목이다.
 * `/uninvited`는 세션 없이 보는 화면이므로 세션 확인보다 먼저 판단한다.
 */
export default function App() {
  const session = useSession();
  const path = window.location.pathname;
  const error = new URLSearchParams(window.location.search).get("error") ?? undefined;

  if (path === "/uninvited") {
    return <UninvitedPage />;
  }
  if (session.state === "loading") {
    return <main className="centered">불러오는 중입니다.</main>;
  }
  if (session.state === "anonymous") {
    return <LoginPage returnTo={path === "/" || path === "/login" ? undefined : path} error={error} />;
  }
  return <ProjectHomePage csrfToken={session.me.csrfToken} />;
}
