import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import ProjectHomePage from "../src/features/projects/ProjectHomePage";
import type { ProjectItem } from "../src/features/projects/types";
import { formatMoment, syncLabelOf } from "../src/features/sync/syncLabels";

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

function project(overrides: Partial<ProjectItem>): ProjectItem {
  return {
    id: "p1",
    githubRepositoryId: "101",
    fullName: "UncleSamsun/syncdoc",
    branch: "main",
    docsRoot: "docs",
    githubProjectNodeId: null,
    currentSnapshotId: null,
    syncState: "queued",
    lastSuccessAt: null,
    syncErrorCode: null,
    documentCount: null,
    version: 0,
    manageable: true,
    ...overrides,
  };
}

function renderWith(item: ProjectItem) {
  stubApi((url) => {
    if (url.endsWith("/projects")) return json({ items: [item] });
    if (url.endsWith("/github/repositories")) return json({ items: [], complete: true });
    throw new Error("unexpected " + url);
  });
  render(<ProjectHomePage csrfToken="c1" />);
}

describe("수집 상태 표시", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("shows a connected project as waiting for its first collection", async () => {
    renderWith(project({ syncState: "queued", currentSnapshotId: null }));

    await waitFor(() => expect(screen.getByText("첫 수집 대기")).toBeInTheDocument());
    // 대기를 문서 없음으로 보이게 하지 않는다.
    expect(screen.queryByText(/문서 0건/)).not.toBeInTheDocument();
    expect(screen.getByText(/성공한 수집 없음/)).toBeInTheDocument();
  });

  it("separates a refresh that is waiting from a first collection", async () => {
    renderWith(project({ syncState: "queued", currentSnapshotId: "s1", documentCount: 12 }));

    await waitFor(() => expect(screen.getByText("갱신 대기")).toBeInTheDocument());
  });

  it("keeps showing the last success time when a refresh failed", async () => {
    renderWith(
      project({
        syncState: "failed",
        currentSnapshotId: "s1",
        lastSuccessAt: "2026-09-11T02:30:00Z",
        syncErrorCode: "GITHUB_UNAVAILABLE",
        documentCount: 12,
      }),
    );

    await waitFor(() => expect(screen.getByText("갱신 실패")).toBeInTheDocument());
    expect(screen.getByText("오류 GITHUB_UNAVAILABLE")).toBeInTheDocument();
    expect(screen.getByText(/문서 12건/)).toBeInTheDocument();
    // 마지막 정상 데이터를 계속 보여 주되 최신이라고 쓰지 않는다.
    expect(screen.queryByText("최신")).not.toBeInTheDocument();
  });

  it("shows a document count only when there is a published snapshot", async () => {
    renderWith(project({ syncState: "succeeded", currentSnapshotId: "s1", documentCount: 0 }));

    await waitFor(() => expect(screen.getByText("최신")).toBeInTheDocument());
    // 게시본이 있고 문서가 실제로 0건인 경우는 0건으로 보여 준다.
    expect(screen.getByText(/문서 0건/)).toBeInTheDocument();
  });

  it("never leaks an error code into a healthy row", async () => {
    renderWith(project({ syncState: "succeeded", currentSnapshotId: "s1", syncErrorCode: "X" }));

    await waitFor(() => expect(screen.getByText("최신")).toBeInTheDocument());
    expect(screen.queryByText(/오류 X/)).not.toBeInTheDocument();
  });
});

describe("수집 상태 라벨", () => {
  it("names every state a person can be shown", () => {
    expect(syncLabelOf({ syncState: "running", currentSnapshotId: null })).toBe("수집 중");
    expect(syncLabelOf({ syncState: "failed", currentSnapshotId: "s1" })).toBe("갱신 실패");
    expect(syncLabelOf({ syncState: "succeeded", currentSnapshotId: "s1" })).toBe("최신");
    expect(syncLabelOf({ syncState: "queued", currentSnapshotId: null })).toBe("첫 수집 대기");
  });

  it("leaves a missing time empty instead of inventing one", () => {
    expect(formatMoment(null)).toBeNull();
    expect(formatMoment("헛소리")).toBeNull();
  });
});
