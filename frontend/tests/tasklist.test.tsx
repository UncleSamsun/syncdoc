import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import TaskListPage from "../src/features/dashboard/TaskListPage";
import { forgetChecklist } from "../src/features/spec/useChecklist";

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
  currentSnapshotId: "s1cafe00-0000-0000-0000-000000000000",
  syncState: "succeeded" as const,
  lastSuccessAt: "2026-09-22T01:00:00Z",
  lastAttemptAt: "2026-09-22T01:00:00Z",
  syncErrorCode: null,
  documentCount: 3,
  version: 1,
  manageable: true,
};

/** 현황 표가 싣는 20건을 넘겨 만든다. 여기서 나머지가 보이는지가 이 화면의 존재 이유다. */
function tasks(count = 26) {
  return Array.from({ length: count }, (_, index) => ({
    taskSpecId: `TASK-${String(index + 1).padStart(3, "0")}`,
    title: `작업 ${index + 1}`,
    documentId: "d1",
    anchor: `task-${index + 1}`,
    issueNumber: index % 3 === 0 ? null : 100 + index,
    issueTitle: null,
    status: index % 3 === 0 ? "unregistered" : index % 3 === 1 ? "done" : "canceled",
    assignees: index % 2 === 0 ? ["UncleSamsun"] : [],
    pullRequests: [],
    mappingConflict: false,
    observedAt: "2026-09-22T01:00:00Z",
  }));
}

function api(list: unknown = { snapshotId: project.currentSnapshotId, items: tasks(), total: 26,
  nextCursor: null }) {
  return (url: string) => {
    if (url.includes("/tasks")) return json(list);
    if (url.includes("/spec-checklist")) {
      return json({ snapshotId: "s1", sourceRevision: "abc", status: "pass", uncheckedReason: null,
        truncated: false, findings: [], types: [] });
    }
    if (url.includes("/documents")) return json({ snapshotId: "s1", items: [], nextCursor: null });
    if (url.includes("/projects/")) return json(project);
    throw new Error("unexpected " + url);
  };
}

let seen = "";

function Address() {
  seen = useLocation().search;
  return null;
}

function open(initial = "/projects/p1/tasks") {
  render(
    <MemoryRouter initialEntries={[initial]}>
      <Address />
      <Routes>
        <Route path="/projects/:projectId/tasks" element={<TaskListPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("UI-014 작업 전체 목록", () => {
  beforeEach(() => {
    seen = "";
    forgetChecklist("p1");
  });
  afterEach(() => vi.unstubAllGlobals());

  it("shows every task, not just the twenty the overview carries", async () => {
    stubApi(api());
    open();

    await waitFor(() => expect(screen.getByRole("heading", { name: "작업" })).toBeInTheDocument());
    expect(screen.getAllByRole("row")).toHaveLength(27); // 머리글 한 줄 + 26건
    expect(screen.getByText("TASK-026")).toBeInTheDocument();
    expect(screen.getByText("26건 중 26건")).toBeInTheDocument();
  });

  it("keeps a filter in the address so the same list comes back", async () => {
    stubApi(api());
    open();

    await waitFor(() => expect(screen.getByLabelText("상태")).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText("상태"), "done");

    expect(seen).toContain("status=done");
    // 거른 탓에 줄어든 것을 전체가 줄어든 것으로 읽히게 하지 않는다.
    expect(screen.getByText(/걸러진 \d+건 \/ 전체 26건/)).toBeInTheDocument();
  });

  it("reads the filter from the address on the first render", async () => {
    stubApi(api());
    open("/projects/p1/tasks?status=canceled");

    await waitFor(() => expect(screen.getByText(/걸러진/)).toBeInTheDocument());
    const rows = screen.getAllByRole("row").slice(1);
    // 취소는 완료와 다른 표시를 쓰고 분모 제외를 밝힌다.
    rows.forEach((row) => expect(within(row).getByText("취소 · 제외")).toBeInTheDocument());
  });

  it("offers only the people who actually appear in the list", async () => {
    stubApi(api());
    open();

    await waitFor(() => expect(screen.getByLabelText("담당")).toBeInTheDocument());
    const options = within(screen.getByLabelText("담당")).getAllByRole("option")
      .map((option) => option.textContent);
    expect(options).toEqual(["전체", "UncleSamsun"]);
  });

  it("does not leave the reader stuck when a filter matches nothing", async () => {
    stubApi(api({ snapshotId: project.currentSnapshotId, items: tasks(3), total: 3,
      nextCursor: null }));
    open("/projects/p1/tasks?assignee=nobody");

    await waitFor(() =>
      expect(screen.getByText("조건에 맞는 작업이 없습니다")).toBeInTheDocument());
    await userEvent.click(screen.getAllByRole("button", { name: "거르기 지우기" })[0]);

    expect(screen.getByText("TASK-001")).toBeInTheDocument();
    expect(seen).not.toContain("assignee");
  });

  it("keeps tasks without an issue in the list", async () => {
    stubApi(api());
    open();

    await waitFor(() => expect(screen.getByText("TASK-001")).toBeInTheDocument());
    const row = screen.getByText("TASK-001").closest("tr") as HTMLElement;
    // Issue 미등록 작업이 목록에서 사라지지 않는다.
    expect(within(row).getByText("Issue 미등록")).toBeInTheDocument();
    // Issue 칸과 PR 칸이 모두 `—`다. 둘 다 없는 것을 빈칸으로 두지 않는다.
    expect(within(row).getAllByText("—")).toHaveLength(2);
  });

  it("has no control that changes a task state", async () => {
    stubApi(api());
    open();

    await waitFor(() => expect(screen.getByText("TASK-001")).toBeInTheDocument());
    // 상태는 GitHub가 정본이다. 여기서 바꾸지 않는다.
    const labels = screen.queryAllByRole("button").map((button) => button.textContent ?? "");
    expect(labels.some((label) => /완료|취소로|상태 바꾸기|배정/.test(label))).toBe(false);
    expect(screen.getByText(/상태는 GitHub가 정본입니다/)).toBeInTheDocument();
  });

  it("shows the first sync screen before the first collection", async () => {
    stubApi(api({ snapshotId: null, items: [], total: 0, nextCursor: null }));
    open();

    await waitFor(() => expect(screen.getByText("첫 수집을 기다리는 중")).toBeInTheDocument());
  });
});
