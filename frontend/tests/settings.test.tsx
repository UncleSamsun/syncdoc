import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SettingsPage from "../src/features/projects/SettingsPage";
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

const me = { id: "u1", githubUserId: "79427050", login: "UncleSamsun", serviceAdmin: true,
  csrfToken: "csrf-1" };

function project(overrides: Record<string, unknown> = {}) {
  return {
    id: "p1",
    githubRepositoryId: "101",
    fullName: "UncleSamsun/syncdoc",
    branch: "dev",
    docsRoot: "docs",
    githubProjectNodeId: null,
    currentSnapshotId: "s1",
    syncState: "succeeded" as const,
    lastSuccessAt: "2026-09-22T01:00:00Z",
    lastAttemptAt: "2026-09-22T01:00:00Z",
    syncErrorCode: null,
    documentCount: 3,
    version: 12,
    manageable: true,
    ...overrides,
  };
}

function api(current = project(), onPatch?: (body: unknown) => Response) {
  return (url: string, init?: RequestInit) => {
    if (init?.method === "PATCH") {
      return onPatch ? onPatch(JSON.parse(String(init.body))) : json(current);
    }
    if (url.endsWith("/me")) return json(me);
    if (url.includes("/spec-checklist")) {
      return json({ snapshotId: "s1", sourceRevision: "abc", status: "pass", uncheckedReason: null,
        truncated: false, findings: [], types: [] });
    }
    if (url.includes("/documents")) return json({ snapshotId: "s1", items: [], nextCursor: null });
    if (url.endsWith("/sync")) {
      return json({ snapshotId: "s1", state: "succeeded", lastAttemptAt: null,
        lastSuccessAt: "2026-09-22T01:00:00Z", errorCode: null, nextRetryAt: null, pending: false });
    }
    if (url.includes("/projects/")) return json(current);
    throw new Error("unexpected " + url);
  };
}

function open() {
  render(
    <MemoryRouter initialEntries={["/projects/p1/settings"]}>
      <Routes>
        <Route path="/projects/:projectId/settings" element={<SettingsPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("UI-015 연결 설정", () => {
  beforeEach(() => forgetChecklist("p1"));
  afterEach(() => vi.unstubAllGlobals());

  it("sends the version it saw when saving", async () => {
    stubApi(api());
    open();

    const input = await screen.findByLabelText("문서 경로");
    await userEvent.clear(input);
    await userEvent.type(input, "spec");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      const patch = calls.find((call) => call.init?.method === "PATCH");
      expect(patch).toBeDefined();
      expect(JSON.parse(String(patch!.init?.body)))
        .toEqual({ docsRoot: "spec", githubProjectNodeId: "", expectedVersion: 12 });
      expect(new Headers(patch!.init?.headers).get("X-CSRF-Token")).toBe("csrf-1");
    });
    expect(await screen.findByText("저장했습니다")).toBeInTheDocument();
  });

  it("shows the server's own words for a rejected path", async () => {
    stubApi(api(project(), () =>
      json({ code: "INVALID_REQUEST", message: "이 브랜치에 spec 경로가 없습니다. 실제 경로를 선택하세요.",
        requestId: "r1", details: { field: "docsRoot" } }, 422)));
    open();

    const input = await screen.findByLabelText("문서 경로");
    await userEvent.clear(input);
    await userEvent.type(input, "spec");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    // 서버가 준 문구를 다른 말로 바꾸지 않는다. 고쳐야 할 것이 무엇인지 그 문장이 말한다.
    expect(await screen.findByText("이 브랜치에 spec 경로가 없습니다. 실제 경로를 선택하세요."))
      .toBeInTheDocument();
  });

  it("does not resend what the reader typed after a version conflict", async () => {
    stubApi(api(project(), () =>
      json({ code: "VERSION_CONFLICT", message: "다른 사용자가 연결 설정을 먼저 바꿨습니다.",
        requestId: "r1", details: {} }, 409)));
    open();

    const input = await screen.findByLabelText("문서 경로");
    await userEvent.clear(input);
    await userEvent.type(input, "spec");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText(/다른 사용자가 연결 설정을 먼저 바꿨습니다/)).toBeInTheDocument();
    // 쓰던 값을 덮어써 보내지 않는다. 최신 설정을 읽어 다시 시작한다.
    await waitFor(() => expect(screen.getByLabelText("문서 경로")).toHaveValue("docs"));
    expect(calls.filter((call) => call.init?.method === "PATCH")).toHaveLength(1);
  });

  it("hides the way to change anything from someone who cannot", async () => {
    stubApi(api(project({ manageable: false })));
    open();

    await waitFor(() =>
      expect(screen.getByText("연결한 사람 또는 서비스 관리자만 바꿀 수 있습니다.")).toBeInTheDocument());
    // 누를 수 있게 보이는데 거절당하는 단추를 두지 않는다.
    expect(screen.queryByRole("button", { name: "저장" })).toBeNull();
    expect(screen.getByLabelText("문서 경로")).toHaveAttribute("readonly");
  });

  it("has no way to disconnect the repository", async () => {
    stubApi(api());
    open();

    await waitFor(() => expect(screen.getByRole("heading", { name: "연결 설정" })).toBeInTheDocument());
    // 그 계약이 없다. 되돌릴 수 없는 일을 단추 하나로 만들지 않는다.
    const labels = screen.queryAllByRole("button").map((button) => button.textContent ?? "");
    expect(labels.some((label) => /해제|끊기|삭제|연결 끊/.test(label))).toBe(false);
    expect(screen.getByText(/연결한 저장소는 바꾸지 않습니다/)).toBeInTheDocument();
  });

  it("says the branch is changed somewhere else", async () => {
    stubApi(api());
    open();

    // 같은 값을 두 곳에서 바꾸게 하면 어느 쪽이 방금 쓴 값인지 알 수 없다. 스위처는 상단바에만
    // 있고 이 화면의 양식에는 없다.
    await waitFor(() => expect(screen.getByText(/상단바에서 바꿉니다/)).toBeInTheDocument());
    const form = document.querySelector(".settings form") as HTMLElement;
    expect(within(form).queryByLabelText("기준 브랜치")).toBeNull();
  });
});
