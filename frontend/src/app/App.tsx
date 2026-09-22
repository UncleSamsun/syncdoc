import { Route, Routes } from "react-router-dom";
import LoginPage from "../features/auth/LoginPage";
import UninvitedPage from "../features/auth/UninvitedPage";
import { useSession } from "../features/auth/useSession";
import OverviewPage from "../features/dashboard/OverviewPage";
import SearchPage from "../features/dashboard/SearchPage";
import TaskListPage from "../features/dashboard/TaskListPage";
import ChecklistPage from "../features/spec/ChecklistPage";
import DiagramFixturePage from "../features/documents/DiagramFixturePage";
import DocumentPage from "../features/documents/DocumentPage";
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
  if (path === "/fixtures/diagrams" && import.meta.env.DEV) {
    // 개발 서버에서만 여는 확인용 화면이다. 배포 빌드에는 들어가지 않는다.
    return <DiagramFixturePage />;
  }
  if (session.state === "loading") {
    return <main className="centered">불러오는 중입니다.</main>;
  }
  if (session.state === "anonymous") {
    return <LoginPage returnTo={path === "/" || path === "/login" ? undefined : path} error={error} />;
  }

  return (
    <Routes>
      <Route path="/" element={<ProjectHomePage csrfToken={session.me.csrfToken} />} />
      <Route path="/projects/:projectId" element={<OverviewPage csrfToken={session.me.csrfToken} />} />
      <Route path="/projects/:projectId/checklist" element={<ChecklistPage />} />
      <Route path="/projects/:projectId/tasks" element={<TaskListPage />} />
      <Route path="/projects/:projectId/search" element={<SearchPage />} />
      <Route path="/projects/:projectId/documents" element={<DocumentPage />} />
      <Route path="/projects/:projectId/documents/:documentId" element={<DocumentPage />} />
      <Route path="*" element={<ProjectHomePage csrfToken={session.me.csrfToken} />} />
    </Routes>
  );
}
