import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import OverviewPage from "../src/features/dashboard/OverviewPage";
import { forgetChecklist } from "../src/features/spec/useChecklist";

type Handler = (url: string, init?: RequestInit) => Response;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

let calls: { url: string; init?: RequestInit }[] = [];

function stubApi(handler: Handler) {
  calls = [];
  vi.stubGlobal("fetch", vi.fn((url: string, init?: RequestInit) => {
    calls.push({ url: String(url), init });
    return Promise.resolve(handler(String(url), init).clone());
  }));
}

const me = {
  id: "u1",
  githubUserId: "79427050",
  login: "UncleSamsun",
  serviceAdmin: true,
  csrfToken: "csrf-1",
};

function project(overrides: Record<string, unknown> = {}) {
  return {
    id: "p1",
    githubRepositoryId: "101",
    fullName: "UncleSamsun/syncdoc",
    branch: "main",
    docsRoot: "docs",
    githubProjectNodeId: null,
    currentSnapshotId: "s1",
    syncState: "succeeded" as const,
    lastSuccessAt: "2026-09-22T01:00:00Z",
    lastAttemptAt: "2026-09-22T01:00:00Z",
    syncErrorCode: null,
    documentCount: 3,
    version: 7,
    manageable: true,
    ...overrides,
  };
}

const overview = {
  snapshotId: "s1",
  sourceRevision: "1549821e8d2c",
  repositoryObservedAt: "2026-09-22T01:00:00Z",
  projectObservedAt: null,
  projectAccess: "ok",
  counts: { notStarted: 0, inProgress: 0, inReview: 0, done: 1, unregistered: 0, canceled: 0, total: 1 },
  progress: { completed: 1, denominator: 1, ratio: 1 },
  taskTotal: 1,
  documentCount: 3,
  tasks: [],
  unmatchedIssueTasks: [],
  projectProgress: null,
  recentChanges: [],
  partial: { documents: false, issues: false, project: false },
};

function api(current = project(), branches = ["main", "dev"]) {
  return (url: string) => {
    if (url.endsWith("/me")) return json(me);
    if (url.includes("/branches")) {
      return json({ items: branches.map((name) => ({ name, isDefault: name === "main" })) });
    }
    if (url.includes("/overview")) return json(overview);
    if (url.includes("/spec-checklist")) {
      return json({ snapshotId: "s1", sourceRevision: "abc", status: "pass", uncheckedReason: null,
        truncated: false, findings: [], types: [] });
    }
    if (url.includes("/documents")) return json({ snapshotId: "s1", items: [], nextCursor: null });
    if (url.endsWith("/sync")) {
      return json({ snapshotId: "s1", state: current.syncState, lastAttemptAt: current.lastAttemptAt,
        lastSuccessAt: current.lastSuccessAt, errorCode: null, nextRetryAt: null, pending: false });
    }
    if (url.includes("/projects/")) return json(current);
    throw new Error("unexpected " + url);
  };
}

function open() {
  render(
    <MemoryRouter initialEntries={["/projects/p1"]}>
      <Routes>
        <Route path="/projects/:projectId" element={<OverviewPage csrfToken="csrf-1" />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("UI-000 공통 셸의 조작", () => {
  beforeEach(() => forgetChecklist("p1"));
  afterEach(() => vi.unstubAllGlobals());

  it("lets a signed-in reader log out for real", async () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, assign, reload: vi.fn() });
    stubApi(api());
    open();

    await waitFor(() => expect(screen.getByText("UncleSamsun")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: "로그아웃" }));

    // 화면만 옮기면 쿠키가 살아 있어 주소만 바꾸면 다시 들어올 수 있다. 서버 세션을 끊는다.
    await waitFor(() => {
      const logout = calls.find((call) => call.url.endsWith("/logout"));
      expect(logout).toBeDefined();
      expect(logout!.init?.method).toBe("POST");
      expect(new Headers(logout!.init?.headers).get("X-CSRF-Token")).toBe("csrf-1");
    });
    await waitFor(() => expect(assign).toHaveBeenCalledWith("/login"));
  });

  it("offers only the branches the repository has, with no free typing", async () => {
    stubApi(api());
    open();

    const picker = await screen.findByLabelText("기준 브랜치");
    expect(picker.tagName).toBe("SELECT");
    await userEvent.click(picker);

    await waitFor(() =>
      expect(within(picker).getAllByRole("option").map((o) => o.textContent)).toEqual(["main", "dev"]));
  });

  it("sends the version it saw when switching a branch", async () => {
    vi.stubGlobal("location", { ...window.location, assign: vi.fn(), reload: vi.fn() });
    stubApi(api());
    open();

    const picker = await screen.findByLabelText("기준 브랜치");
    // 목록은 열 때 받는다. 미리 받아 두면 쓰지도 않을 요청이 화면마다 늘어난다.
    await userEvent.click(picker);
    await waitFor(() => expect(within(picker).getAllByRole("option")).toHaveLength(2));
    await userEvent.selectOptions(picker, "dev");

    await waitFor(() => {
      const patch = calls.find((call) => call.init?.method === "PATCH");
      expect(patch).toBeDefined();
      expect(JSON.parse(String(patch!.init?.body))).toEqual({ branch: "dev", expectedVersion: 7 });
      expect(new Headers(patch!.init?.headers).get("X-CSRF-Token")).toBe("csrf-1");
    });
  });

  it("does not open the branch list for someone who cannot change it", async () => {
    stubApi(api(project({ manageable: false })));
    open();

    await waitFor(() => expect(screen.getByTitle(/연결자 또는 서비스 관리자만/)).toBeInTheDocument());
    expect(screen.queryByLabelText("기준 브랜치")).toBeNull();
  });

  it("says which branch is being collected while it runs", async () => {
    stubApi(api(project({ syncState: "running" })));
    open();

    // 브랜치를 바꾼 직후 이전 게시본을 보는 사람이 무엇을 기다리는지 알 수 있어야 한다.
    await waitFor(() => expect(screen.getByText(/main 수집 중/)).toBeInTheDocument());
  });
});
