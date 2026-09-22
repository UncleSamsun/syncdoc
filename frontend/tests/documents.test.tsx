import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import DocumentPage from "../src/features/documents/DocumentPage";

const renderMermaid = vi.fn();

vi.mock("mermaid", () => ({
  default: {
    initialize: vi.fn(),
    render: (...args: unknown[]) => renderMermaid(...args),
  },
}));

type Handler = (url: string) => Response;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function error(code: string, status: number): Response {
  return json({ code, message: "", requestId: "r", details: {} }, status);
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
  documentCount: 2,
  version: 0,
  manageable: true,
};

const okStatus = {
  state: "succeeded" as const,
  lastAttemptAt: "2026-09-21T01:00:00Z",
  lastSuccessAt: "2026-09-21T01:00:00Z",
  errorCode: null,
  nextRetryAt: null,
  pending: false,
};

const items = [
  { id: "d1", path: "docs/01-prd/brief.md", title: "요구 정리", kind: "prd-overview" },
  { id: "d2", path: "docs/03-tech-spec/api-spec.md", title: "API 계약", kind: "tech-interface" },
];

const body = {
  id: "d2",
  snapshotId: "s1",
  sourceRevision: "1549821e8d2c0a05",
  path: "docs/03-tech-spec/api-spec.md",
  title: "API 계약",
  specId: "DOC-010",
  kind: "tech-interface",
  html: '<h2 id="api-018-문서-본문">API-018 문서 본문</h2><table><tr><th>ID</th></tr></table>'
    + '<div class="diagram" data-diagram-id="d1"></div>',
  headings: [{ level: 2, id: "api-018-문서-본문", text: "API-018 문서 본문" }],
  diagrams: [{ id: "d1", syntax: "mermaid", source: "flowchart TD\n  A --> B" }],
  links: [],
  warnings: [{ code: "LINK_TARGET_NOT_FOUND", detail: "../../AGENTS.md" }],
};

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
    if (url.includes("/documents/")) {
      return overrides.document ?? json(body);
    }
    if (url.includes("/documents")) {
      return overrides.list ?? json({ snapshotId: "s1", items, nextCursor: null });
    }
    if (url.endsWith("/sync")) {
      return overrides.status ?? json(okStatus);
    }
    if (url.includes("/projects/")) {
      return overrides.project ?? json(project);
    }
    throw new Error("unexpected " + url);
  };
}

function open(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/projects/:projectId" element={<DocumentPage />} />
        <Route path="/projects/:projectId/documents/:documentId" element={<DocumentPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("UI-003 문서 본문", () => {
  beforeEach(() => {
    renderMermaid.mockReset();
    renderMermaid.mockResolvedValue({ svg: "<svg data-testid='drawn'></svg>" });
    window.localStorage.clear();
  });

  afterEach(() => vi.unstubAllGlobals());

  it("shows the body, its meta line and the table of contents", async () => {
    stubApi(api());
    open("/projects/p1/documents/d2");

    await waitFor(() => expect(screen.getByRole("heading", { name: "API 계약" })).toBeInTheDocument());
    expect(screen.getByText("docs/03-tech-spec/api-spec.md")).toBeInTheDocument();
    expect(screen.getByText("1549821e8d")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "API-018 문서 본문" })).toHaveAttribute(
      "href",
      "#api-018-문서-본문",
    );
  });

  it("puts each table in its own scrolling box", async () => {
    stubApi(api());
    open("/projects/p1/documents/d2");

    await waitFor(() => expect(document.querySelector("table")).toBeInTheDocument());
    expect(document.querySelector(".tblwrap > table.mdtbl")).toBeInTheDocument();
  });

  it("drops the repeated title from the body but keeps the anchor that points at it", async () => {
    const titled = {
      ...body,
      html: '<h1 id="api-계약">API 계약</h1><p>본문</p>',
      headings: [{ level: 1, id: "api-계약", text: "API 계약" }],
      diagrams: [],
    };
    stubApi(api({ document: json(titled) }));
    open("/projects/p1/documents/d2");

    await waitFor(() => expect(document.querySelector(".doc-body")).toBeInTheDocument());
    const docBody = document.querySelector(".doc-body") as HTMLElement;
    // 제목은 화면 위에 이미 있다. 본문에서 한 번 더 읽게 하지 않는다.
    expect(docBody.querySelector("h1")).toBeNull();
    // 검색 결과와 목차가 이 id로 찾아온다. 제목을 뺀다고 앵커까지 없애면 그 링크가 죽는다.
    expect(document.getElementById("api-계약")).not.toBeNull();
  });

  it("opens a file outside the collected root in a new tab and says where it goes", async () => {
    const outside = {
      ...body,
      html: '<p><a href="https://github.com/o/r/blob/abc/rules/validation.md" '
        + 'data-link-kind="source">검증 규칙</a></p>',
      diagrams: [],
    };
    stubApi(api({ document: json(outside) }));
    open("/projects/p1/documents/d2");

    await waitFor(() => expect(screen.getByRole("link", { name: /검증 규칙/ })).toBeInTheDocument());
    const link = screen.getByRole("link", { name: /검증 규칙/ });
    // 서비스를 벗어난다. 읽던 자리를 잃지 않게 새 탭이고 어디로 가는지 미리 알린다.
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
    expect(link.getAttribute("title")).toContain("github.com/o/r/blob/abc/rules/validation.md");
  });

  it("collects warnings next to the table of contents instead of in the body", async () => {
    stubApi(api());
    open("/projects/p1/documents/d2");

    await waitFor(() => expect(screen.getByText("경고 1")).toBeInTheDocument());
    const toc = document.querySelector(".toc") as HTMLElement;
    expect(within(toc).getByText("LINK_TARGET_NOT_FOUND")).toBeInTheDocument();
    const docBody = document.querySelector(".doc-body") as HTMLElement;
    expect(docBody.textContent).not.toContain("LINK_TARGET_NOT_FOUND");
  });

  it("draws a diagram where its placeholder is", async () => {
    stubApi(api());
    open("/projects/p1/documents/d2");

    await waitFor(() => expect(renderMermaid).toHaveBeenCalled());
    expect(renderMermaid.mock.calls[0][1]).toContain("flowchart TD");
    await waitFor(() =>
      expect(document.querySelector('[data-diagram-id="d1"] svg')).toBeInTheDocument(),
    );
  });

  it("keeps the rest of the document readable when one diagram fails", async () => {
    renderMermaid.mockRejectedValue(new Error("문법이 올바르지 않습니다"));
    stubApi(api());
    open("/projects/p1/documents/d2");

    await waitFor(() =>
      expect(screen.getByText("이 블록만 표시되지 않습니다.")).toBeInTheDocument(),
    );
    expect(screen.getByText("문서의 나머지는 그대로 읽을 수 있습니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /원문 보기/ })).toBeInTheDocument();
    // 본문은 그대로 남는다.
    expect(screen.getByRole("heading", { name: "API 계약" })).toBeInTheDocument();
  });

  it("does not even try to draw a diagram over the size limit", async () => {
    const big = { ...body, diagrams: [{ id: "d1", syntax: "mermaid", source: "flowchart TD\n" + "A-->B\n".repeat(4000) }] };
    stubApi(api({ document: json(big) }));
    open("/projects/p1/documents/d2");

    await waitFor(() => expect(screen.getByText("원문이 너무 큽니다")).toBeInTheDocument());
    expect(renderMermaid).not.toHaveBeenCalled();
  });

  it("refuses a diagram type that is not supported", async () => {
    const odd = { ...body, diagrams: [{ id: "d1", syntax: "mermaid", source: "gantt\n title 일정" }] };
    stubApi(api({ document: json(odd) }));
    open("/projects/p1/documents/d2");

    await waitFor(() => expect(screen.getByText("지원하지 않는 유형입니다")).toBeInTheDocument());
    expect(renderMermaid).not.toHaveBeenCalled();
  });
});

describe("문서 트리", () => {
  beforeEach(() => {
    window.localStorage.clear();
    renderMermaid.mockResolvedValue({ svg: "<svg></svg>" });
  });

  afterEach(() => vi.unstubAllGlobals());

  it("mirrors the folders of the repository", async () => {
    stubApi(api());
    open("/projects/p1");

    await waitFor(() => expect(screen.getByRole("button", { name: /01-prd/ })).toBeInTheDocument());
    expect(screen.getByRole("button", { name: /03-tech-spec/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "요구 정리" })).toBeInTheDocument();
  });

  it("remembers a collapsed folder for the next visit", async () => {
    stubApi(api());
    open("/projects/p1");

    const folder = await screen.findByRole("button", { name: /01-prd/ });
    expect(screen.getByRole("link", { name: "요구 정리" })).toBeInTheDocument();
    await userEvent.click(folder);

    expect(screen.queryByRole("link", { name: "요구 정리" })).not.toBeInTheDocument();
    expect(window.localStorage.getItem("syncdoc.tree.collapsed.p1")).toContain("docs/01-prd");
  });

  it("opens the folder of the document being read even when it was collapsed", async () => {
    window.localStorage.setItem("syncdoc.tree.collapsed.p1", JSON.stringify(["docs/03-tech-spec"]));
    stubApi(api());
    open("/projects/p1/documents/d2");

    await waitFor(() =>
      expect(screen.getByRole("link", { name: "API 계약" })).toHaveAttribute("aria-current", "page"),
    );
  });
});

describe("문서 화면의 상태", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("waits for the first collection instead of saying there are no documents", async () => {
    stubApi(api({ list: json({ snapshotId: null, items: [], nextCursor: null }) }));
    open("/projects/p1");

    await waitFor(() => expect(screen.getByText("첫 수집을 기다리는 중")).toBeInTheDocument());
    expect(screen.queryByText(/문서가 없습니다/)).not.toBeInTheDocument();
  });

  it("uses one wording for a missing document and one we may not see", async () => {
    stubApi(api({ document: error("RESOURCE_NOT_FOUND", 404) }));
    open("/projects/p1/documents/d9");

    await waitFor(() => expect(screen.getByText("이 문서를 찾을 수 없습니다")).toBeInTheDocument());
    expect(
      screen.getByText("경로가 바뀌었거나 접근 권한이 없습니다. 문서 목록에서 다시 찾아보세요."),
    ).toBeInTheDocument();
  });

  it("offers a way back when the snapshot was revoked", async () => {
    stubApi(api({ list: error("SNAPSHOT_GONE", 410) }));
    open("/projects/p1/documents/d2?snapshotId=old");

    await waitFor(() =>
      expect(screen.getByText("이 버전은 더 이상 제공되지 않습니다")).toBeInTheDocument(),
    );
    expect(screen.getByRole("link", { name: "최신 문서 목록으로" })).toHaveAttribute(
      "href",
      "/projects/p1",
    );
  });

  it("says a document cannot be shown when it was not converted", async () => {
    stubApi(api({ document: error("DOCUMENT_NOT_RENDERABLE", 422) }));
    open("/projects/p1/documents/d2");

    await waitFor(() => expect(screen.getByText("이 문서를 표시할 수 없습니다")).toBeInTheDocument());
  });

  it("shows the failure banner with the time the data is based on", async () => {
    stubApi(
      api({
        status: json({
          state: "failed",
          lastAttemptAt: "2026-09-21T02:00:00Z",
          lastSuccessAt: "2026-09-21T01:00:00Z",
          errorCode: "GITHUB_UNAVAILABLE",
          nextRetryAt: "2026-09-21T02:05:00Z",
          pending: true,
        }),
      }),
    );
    open("/projects/p1/documents/d2");

    await waitFor(() => expect(screen.getByText(/이후 갱신 실패/)).toBeInTheDocument());
    expect(
      screen.getByText(
        "아래 내용은 마지막으로 성공한 snapshot입니다. 실패한 수집이 이 데이터를 덮어쓰지 않았습니다.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("GITHUB_UNAVAILABLE")).toBeInTheDocument();
    expect(screen.getByText(/재시도/)).toBeInTheDocument();
    // 마지막 정상 데이터는 계속 보여주되 최신이라고 쓰지 않는다.
    expect(screen.getByRole("heading", { name: "API 계약" })).toBeInTheDocument();
  });
});
