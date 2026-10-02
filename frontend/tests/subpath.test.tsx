import { render, screen, cleanup } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
vi.stubEnv("BASE_URL", "/syncdoc/");
const { default: LoginPage } = await import("../src/features/auth/LoginPage");
const { default: UninvitedPage } = await import("../src/features/auth/UninvitedPage");
const { default: DocumentBody } = await import("../src/features/documents/DocumentBody");
const { apiGet } = await import("../src/shared/api/client");
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
it("keeps login, returnTo and uninvited links under the application", () => {
  render(<LoginPage returnTo="/projects/p/documents/d" />);
  expect(screen.getByRole("link")).toHaveAttribute("href", "/syncdoc/api/v1/auth/github/start?returnTo=%2Fprojects%2Fp%2Fdocuments%2Fd");
  cleanup(); render(<UninvitedPage />);
  expect(screen.getByRole("link")).toHaveAttribute("href", "/syncdoc/login");
});
it("uses the application prefix for API calls", async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response('{}', {headers:{'Content-Type':'application/json'}}));
  vi.stubGlobal("fetch", fetcher);
  await apiGet("/me");
  expect(fetcher.mock.calls[0][0]).toBe("/syncdoc/api/v1/me");
});
it("rebases stored document and asset links while preserving external links", () => {
  render(<MemoryRouter basename="/syncdoc" initialEntries={["/syncdoc/projects/p"]}><DocumentBody title="Example" diagrams={[]} html={'<a href="/projects/p/documents/d#s">Document</a><img src="/api/v1/projects/p/assets/a?snapshotId=s" alt="Asset"><a href="https://example.com/a">External</a>'}/></MemoryRouter>);
  expect(screen.getByRole("link",{name:"Document"})).toHaveAttribute("href","/syncdoc/projects/p/documents/d#s");
  expect(screen.getByAltText("Asset")).toHaveAttribute("src","/syncdoc/api/v1/projects/p/assets/a?snapshotId=s");
  expect(screen.getByRole("link",{name:"External"})).toHaveAttribute("href","https://example.com/a");
});

it("navigates stored document links within the basename", async () => {
  const user = userEvent.setup();
  render(<MemoryRouter basename="/syncdoc" initialEntries={["/syncdoc/projects/p"]}><Routes>
    <Route path="/projects/p" element={<DocumentBody title="Example" diagrams={[]} html={'<a href="/projects/p/documents/d">Document</a>'}/>} />
    <Route path="/projects/p/documents/d" element={<h1>Target document</h1>} />
  </Routes></MemoryRouter>);
  await user.click(screen.getByRole("link", {name:"Document"}));
  expect(screen.getByRole("heading", {name:"Target document"})).toBeInTheDocument();
});
const { default: ProjectHomePage } = await import("../src/features/projects/ProjectHomePage");
it("keeps project home entry links under the application prefix", async () => {
  vi.stubGlobal("fetch", vi.fn((url: string) => Promise.resolve(new Response(JSON.stringify({items: String(url).endsWith("/projects") ? [{id:"p1",fullName:"owner/repo",branch:"main",docsRoot:"docs",syncState:"succeeded",documentCount:1}] : []}),{headers:{"Content-Type":"application/json"}}))));
  render(<MemoryRouter basename="/syncdoc" initialEntries={["/syncdoc/"]}><ProjectHomePage csrfToken="test" /></MemoryRouter>);
  expect(await screen.findByRole("link",{name:"열기"})).toHaveAttribute("href","/syncdoc/projects/p1");
});
