import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ChecklistPage from "../src/features/spec/ChecklistPage";
import { forgetChecklist } from "../src/features/spec/useChecklist";

type Handler = (url: string) => Response;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function stubApi(handler: Handler) {
  // 같은 응답을 여러 번 읽을 수 있게 복제한다. 본문은 한 번만 읽히므로, 화면이 같은 계약을
  // 두 번 부르면(예: 20초 감시) 두 번째가 빈손이 된다.
  vi.stubGlobal("fetch", vi.fn((url: string) => Promise.resolve(handler(String(url)).clone())));
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
  lastSuccessAt: "2026-09-22T01:00:00Z",
  lastAttemptAt: "2026-09-22T01:00:00Z",
  syncErrorCode: null,
  documentCount: 3,
  version: 1,
  manageable: true,
};

function checklist(overrides: Record<string, unknown> = {}) {
  return {
    snapshotId: "s1cafe00-0000-0000-0000-000000000000",
    sourceRevision: "1549821e8d2c0a05",
    status: "error",
    uncheckedReason: null,
    truncated: false,
    findings: [],
    types: [
      {
        type: "prd-requirements",
        name: "요구",
        apply: "적용",
        reason: "",
        status: "error",
        documents: [{ documentId: "d1", path: "docs/req.md", specId: "DOC-002" }],
        findings: [{
          documentId: "d1",
          path: "docs/req.md",
          line: 9,
          check: "C2",
          message: "REQ-001에 '**근거:**' 항목이 없다",
        }],
      },
      {
        type: "tech-ops",
        name: "실행과 운영",
        apply: "보류",
        reason: "배포 설계 전이다",
        status: "pending",
        documents: [],
        findings: [],
      },
      {
        type: "guide",
        name: "안내서",
        apply: "미적용",
        reason: "해당 없음",
        status: null,
        documents: [],
        findings: [],
      },
    ],
    ...overrides,
  };
}

function api(view: unknown = checklist(), status = 200) {
  return (url: string) => {
    if (url.includes("/spec-checklist")) return json(view, status);
    if (url.includes("/documents")) return json({ snapshotId: "s1", items: [], nextCursor: null });
    if (url.includes("/projects/")) return json(project);
    throw new Error("unexpected " + url);
  };
}

function open() {
  render(
    <MemoryRouter initialEntries={["/projects/p1/checklist"]}>
      <Routes>
        <Route path="/projects/:projectId/checklist" element={<ChecklistPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("UI-013 산출물 체크리스트", () => {
  beforeEach(() => forgetChecklist("p1"));
  afterEach(() => vi.unstubAllGlobals());

  it("counts what is applied, collected, wrong and not written yet", async () => {
    stubApi(api());
    open();

    await waitFor(() =>
      expect(screen.getByRole("heading", { name: "산출물 체크리스트" })).toBeInTheDocument());
    expect(screen.getByText("적용 1종 · 문서 1건 · 오류 1건 · 미작성 1종")).toBeInTheDocument();
  });

  it("keeps 미작성 apart from 오류", async () => {
    stubApi(api());
    open();

    await waitFor(() => expect(screen.getByText("실행과 운영")).toBeInTheDocument());
    const pending = screen.getByText("실행과 운영").closest("tr") as HTMLElement;
    // 보류로 정한 종류의 문서가 아직 없는 것은 오류가 아니다. 사유를 함께 보인다.
    expect(within(pending).getByText("미작성")).toBeInTheDocument();
    expect(within(pending).queryByText(/오류/)).toBeNull();
    expect(within(pending).getByText(/배포 설계 전이다/)).toBeInTheDocument();
  });

  it("leaves the status empty for a type that was never checked", async () => {
    stubApi(api());
    open();

    await waitFor(() => expect(screen.getByText("안내서")).toBeInTheDocument());
    const notApplied = screen.getByText("안내서").closest("tr") as HTMLElement;
    // 미적용은 검사하지 않았다. 통과로 보이게 하지 않는다.
    expect(within(notApplied).queryByText("통과")).toBeNull();
    expect(within(notApplied).getByText(/해당 없음/)).toBeInTheDocument();
  });

  it("opens a type to show where to fix each finding", async () => {
    stubApi(api());
    open();

    await waitFor(() => expect(screen.getByRole("button", { name: "요구" })).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: "요구" }));

    expect(screen.getByText("REQ-001에 '**근거:**' 항목이 없다")).toBeInTheDocument();
    // 오류에서 그 문서의 그 줄로 이어진다. 문서를 다시 찾게 하지 않는다.
    expect(screen.getByRole("link", { name: "docs/req.md:9" }))
      .toHaveAttribute("href", "/projects/p1/documents/d1");
  });

  it("puts the findings that belong to no type above the table", async () => {
    stubApi(api(checklist({
      findings: [{
        documentId: null,
        path: "rules/project-settings.md",
        line: 8,
        check: "C1",
        message: "'ui-screens' 문서가 없다",
      }],
    })));
    open();

    await waitFor(() =>
      expect(screen.getByRole("heading", { name: "종류에 붙지 않는 오류" })).toBeInTheDocument());
    expect(screen.getByText("'ui-screens' 문서가 없다")).toBeInTheDocument();
    // 수집한 문서가 아니라 링크를 걸지 않는다. 없는 자리로 보내지 않는다.
    expect(screen.queryByRole("link", { name: "rules/project-settings.md:8" })).toBeNull();
  });

  it("never shows unchecked as passed", async () => {
    stubApi(api(checklist({
      status: "unchecked",
      uncheckedReason: "DEFINITION_MISSING",
      types: [],
    })));
    open();

    await waitFor(() =>
      expect(screen.getByText("규칙 정의 파일이 없어 검사하지 않았습니다")).toBeInTheDocument());
    expect(screen.getByText(/rules\/spec-format.json/)).toBeInTheDocument();
    expect(screen.getByText("검사하지 않은 것을 통과로 표시하지 않습니다.")).toBeInTheDocument();
    expect(screen.queryByText("통과")).toBeNull();
  });

  it("says a snapshot made before the check was added is not a verdict", async () => {
    stubApi(api(checklist({ status: "unchecked", uncheckedReason: "NOT_COMPUTED", types: [] })));
    open();

    await waitFor(() =>
      expect(screen.getByText("이 게시본은 규약 판정 전에 만들어졌습니다")).toBeInTheDocument());
  });

  it("says so when the list was cut", async () => {
    stubApi(api(checklist({ truncated: true })));
    open();

    await waitFor(() => expect(
      screen.getByText("오류가 많아 일부만 보여줍니다 · 저장소에서 확인하세요")).toBeInTheDocument());
  });

  it("has no control that edits the rules", async () => {
    stubApi(api());
    open();

    await waitFor(() => expect(screen.getByText("요구")).toBeInTheDocument());
    // REQ-008의 경계다. 기준은 저장소에서 고친다.
    const buttons = screen.getAllByRole("button").map((button) => button.textContent ?? "");
    expect(buttons.some((label) => /수정|편집|해제|무시|삭제/.test(label))).toBe(false);
    expect(screen.getByText("기준은 저장소에서 고칩니다. 이 화면은 읽기만 합니다.")).toBeInTheDocument();
  });

  it("shows the first sync screen before the first collection", async () => {
    stubApi(api({ code: "DOCUMENTS_NOT_READY" }, 409));
    open();

    await waitFor(() => expect(screen.getByText("첫 수집을 기다리는 중")).toBeInTheDocument());
  });
});
