import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import OverviewPage from "../src/features/dashboard/OverviewPage";
import SearchPage from "../src/features/dashboard/SearchPage";

type Handler = (url: string) => Response;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function stubApi(handler: Handler) {
  vi.stubGlobal("fetch", vi.fn((url: string) => Promise.resolve(handler(String(url)))));
}

const project = {
  id: "p1",
  githubRepositoryId: "101",
  fullName: "UncleSamsun/syncdoc",
  branch: "main",
  docsRoot: "docs",
  githubProjectNodeId: null,
  currentSnapshotId: "s1",
  syncState: "succeeded" as const,
  lastSuccessAt: "2026-09-21T01:00:00Z",
  syncErrorCode: null,
  documentCount: 19,
  version: 0,
  manageable: true,
};

const task = (overrides: Record<string, unknown> = {}) => ({
  taskSpecId: "TASK-001",
  title: "실행 기반",
  documentId: "d1",
  anchor: "task-001-실행-기반",
  issueNumber: null,
  issueTitle: null,
  status: "unregistered",
  assignees: [],
  pullRequests: [],
  mappingConflict: false,
  observedAt: null,
  ...overrides,
});

const overview = (overrides: Record<string, unknown> = {}) => ({
  snapshotId: "s1",
  sourceRevision: "1549821e8d2c",
  repositoryObservedAt: "2026-09-21T01:00:00Z",
  projectObservedAt: "2026-09-21T01:05:00Z",
  projectAccess: "not_connected",
  counts: {
    notStarted: 0,
    inProgress: 1,
    inReview: 0,
    done: 1,
    unregistered: 1,
    canceled: 1,
    total: 4,
  },
  progress: { completed: 1, denominator: 3, ratio: 0.3333 },
  projectProgress: null,
  documentCount: 19,
  recentChanges: [
    { documentId: "d2", path: "docs/01-prd/mvp-scope.md", title: "MVP 기능", change: "modified" },
  ],
  tasks: [
    task({ taskSpecId: "TASK-001", status: "done", issueNumber: 11, pullRequests: [34, 35, 36], assignees: ["UncleSamsun"] }),
    task({ taskSpecId: "TASK-002", status: "canceled", issueNumber: 12 }),
    task({ taskSpecId: "TASK-003", status: "in_progress", issueNumber: 13 }),
    task({ taskSpecId: "TASK-004", status: "unregistered" }),
  ],
  taskTotal: 4,
  unmatchedIssueTasks: [],
  partial: { documents: false, issues: false, project: false },
  ...overrides,
});

const checklist = {
  snapshotId: "s1",
  sourceRevision: "1549821e8d2c0a05",
  status: "pass",
  uncheckedReason: null,
  truncated: false,
  findings: [],
  types: [],
};

function api(overrides: Partial<Record<string, Response>> = {}) {
  return (url: string) => {
    if (url.includes("/spec-checklist")) return json(checklist);
    if (url.includes("/overview")) return overrides.overview ?? json(overview());
    if (url.includes("/search")) return overrides.search ?? json({ snapshotId: "s1", query: "", items: [], total: 0 });
    if (url.includes("/documents")) return json({ snapshotId: "s1", items: [], nextCursor: null });
    if (url.endsWith("/sync")) return overrides.status ?? json({
      state: "succeeded",
      lastAttemptAt: "2026-09-21T01:00:00Z",
      lastSuccessAt: "2026-09-21T01:00:00Z",
      errorCode: null,
      nextRetryAt: null,
      pending: false,
    });
    if (url.includes("/projects/")) return json(project);
    throw new Error("unexpected " + url);
  };
}

/** 볼 수 없는 프로젝트는 모든 계약이 404로 답한다. 없는 프로젝트와 구분되지 않아야 한다. */
function notFoundApi() {
  return () => json({ code: "RESOURCE_NOT_FOUND", message: "대상을 찾을 수 없습니다." }, 404);
}

function openOverview() {
  render(
    <MemoryRouter initialEntries={["/projects/p1"]}>
      <Routes>
        <Route path="/projects/:projectId" element={<OverviewPage csrfToken="c1" />} />
      </Routes>
    </MemoryRouter>,
  );
}

function openSearch(query = "") {
  render(
    <MemoryRouter initialEntries={[`/projects/p1/search${query ? `?q=${query}` : ""}`]}>
      <Routes>
        <Route path="/projects/:projectId/search" element={<SearchPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("UI-002 현황", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("shows an invisible project exactly like a missing one", async () => {
    stubApi(notFoundApi());
    openOverview();

    // 권한이 없다는 말을 화면에 두지 않는다. 그 문구 자체가 프로젝트의 존재를 알려 준다.
    await waitFor(() => expect(screen.getByText("이 문서를 찾을 수 없습니다")).toBeInTheDocument());
    expect(screen.queryByText(/권한을 확인할 수 없습니다/)).toBeNull();
  });


  it("writes the completion as a fraction with the basis under it", async () => {
    stubApi(api());
    openOverview();

    await waitFor(() => expect(screen.getByText("작업 완료")).toBeInTheDocument());
    // 분모 없이 숫자만 두지 않는다.
    expect(screen.getByText("1 / 3")).toBeInTheDocument();
    expect(screen.getByText("상태 명시 4건 기준 · 취소 1건 제외")).toBeInTheDocument();
  });

  it("keeps a task with no issue in the table instead of dropping it", async () => {
    stubApi(api());
    openOverview();

    await waitFor(() => expect(screen.getByText("TASK-004")).toBeInTheDocument());
    expect(screen.getAllByText("Issue 미등록").length).toBeGreaterThan(0);
  });

  it("marks a canceled task as excluded from the denominator", async () => {
    stubApi(api());
    openOverview();

    await waitFor(() => expect(screen.getByText("취소 · 제외")).toBeInTheDocument());
    expect(screen.getByText(/분모 제외/)).toBeInTheDocument();
  });

  it("lists every pull request of a task without counting them as completions", async () => {
    stubApi(api());
    openOverview();

    await waitFor(() => expect(screen.getByText("#34 #35 #36")).toBeInTheDocument());
    // 완료는 하나다. PR 셋이 완료 셋으로 보이지 않아야 한다.
    const card = screen.getByText("작업 완료").closest(".card") as HTMLElement;
    expect(within(card).getByText("1 / 3")).toBeInTheDocument();
  });

  it("does not draw a zero percent when there is nothing to count", async () => {
    stubApi(
      api({
        overview: json(
          overview({
            counts: { notStarted: 0, inProgress: 0, inReview: 0, done: 0, unregistered: 0, canceled: 0, total: 0 },
            progress: { completed: 0, denominator: 0, ratio: null },
            tasks: [],
            taskTotal: 0,
          }),
        ),
      }),
    );
    openOverview();

    await waitFor(() => expect(screen.getAllByText("계산 대상 없음").length).toBeGreaterThan(0));
    expect(screen.queryByText("0%")).not.toBeInTheDocument();
  });

  it("says a project was never connected instead of calling it a permission problem", async () => {
    stubApi(api());
    openOverview();

    await waitFor(() => expect(screen.getByText("연결 안 함")).toBeInTheDocument());
    expect(
      screen.getByText(/이 프로젝트에 GitHub Project를 연결하지 않았습니다/),
    ).toBeInTheDocument();
    expect(screen.queryByText("조회 불가")).not.toBeInTheDocument();
  });

  it("shows the unavailable wording when the project cannot be read", async () => {
    stubApi(api({ overview: json(overview({ projectAccess: "unavailable" })) }));
    openOverview();

    await waitFor(() => expect(screen.getByText("조회 불가")).toBeInTheDocument());
    expect(screen.getByText("progress: null")).toBeInTheDocument();
    expect(screen.queryByText("연결 안 함")).not.toBeInTheDocument();
  });

  it("keeps the numbers visible but unconfirmed while collection is incomplete", async () => {
    stubApi(
      api({
        overview: json(overview({ partial: { documents: false, issues: true, project: false } })),
      }),
    );
    openOverview();

    await waitFor(() => expect(screen.getAllByText("확정 아님").length).toBeGreaterThan(0));
    expect(screen.getByText(/전체 페이지 수집이 끝나지 않았습니다/)).toBeInTheDocument();
    // 숫자를 숨기지 않는다.
    expect(screen.getByText(/상태 명시 4건 기준/)).toBeInTheDocument();
  });

  it("asks for a collection when the user presses the sync button", async () => {
    const calls: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string, init?: RequestInit) => {
        calls.push(`${init?.method ?? "GET"} ${String(url)}`);
        return Promise.resolve(api()(String(url)));
      }),
    );
    openOverview();

    await waitFor(() => expect(screen.getByRole("button", { name: "지금 동기화" })).toBeEnabled());
    await userEvent.click(screen.getByRole("button", { name: "지금 동기화" }));

    await waitFor(() =>
      expect(calls.some((call) => call.startsWith("POST") && call.includes("/sync"))).toBe(true),
    );
  });

  it("waits for the first collection instead of showing an empty aggregate", async () => {
    stubApi(api({ overview: json(overview({ snapshotId: null, taskTotal: 0, tasks: [] })) }));
    openOverview();

    await waitFor(() => expect(screen.getByText("첫 수집을 기다리는 중")).toBeInTheDocument());
  });
});

describe("UI-004 검색", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("shows a hit with its path, anchor and excerpt", async () => {
    stubApi(
      api({
        search: json({
          snapshotId: "s1",
          query: "임대",
          total: 1,
          items: [
            {
              documentId: "d5",
              path: "docs/03-tech-spec/data-model.md",
              title: "MVP 데이터 모델",
              anchor: "저장과-갱신",
              excerpt: "… 임대 token을 부여한다 …",
            },
          ],
        }),
      }),
    );
    openSearch("임대");

    await waitFor(() => expect(screen.getByText("MVP 데이터 모델")).toBeInTheDocument());
    expect(screen.getByText(/docs\/03-tech-spec\/data-model.md · #저장과-갱신/)).toBeInTheDocument();
    expect(screen.getByText(/임대 token을 부여한다/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "MVP 데이터 모델" })).toHaveAttribute(
      "href",
      "/projects/p1/documents/d5#저장과-갱신",
    );
  });

  it("separates no results from not collected yet", async () => {
    stubApi(api({ search: json({ snapshotId: "s1", query: "없는말", items: [], total: 0 }) }));
    openSearch("없는말");

    await waitFor(() => expect(screen.getByText("일치하는 문서가 없습니다")).toBeInTheDocument());
    expect(screen.getByText(/권한이 없는 문서는 결과에 나타나지 않습니다/)).toBeInTheDocument();
  });

  it("tells the user the query is too long instead of trimming it", async () => {
    stubApi(api());
    openSearch();

    await waitFor(() => expect(screen.getByLabelText("검색어")).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText("검색어"), "가".repeat(201));

    expect(screen.getByRole("alert")).toHaveTextContent("200자까지입니다");
  });
});
