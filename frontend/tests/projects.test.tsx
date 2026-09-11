import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import ProjectHomePage from "../src/features/projects/ProjectHomePage";

type Handler = (url: string, init?: RequestInit) => Response;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** 경로별 응답을 등록한다. 등록하지 않은 경로를 부르면 테스트가 실패한다. */
function stubApi(handler: Handler) {
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string, init?: RequestInit) => Promise.resolve(handler(String(url), init))),
  );
}

const noProjects = { items: [] };

describe("UI-001 프로젝트 홈", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("tells the user when nothing can be connected and why", async () => {
    stubApi((url) => {
      if (url.endsWith("/projects")) return json(noProjects);
      if (url.endsWith("/github/repositories")) return json({ items: [], complete: true });
      throw new Error("unexpected " + url);
    });

    render(<ProjectHomePage csrfToken="c1" />);

    await waitFor(() =>
      expect(screen.getByText("연결할 수 있는 저장소가 없습니다")).toBeInTheDocument(),
    );
    expect(
      screen.getByText("앱과 계정 양쪽에서 접근할 수 있는 저장소만 연결할 수 있습니다."),
    ).toBeInTheDocument();
  });

  it("offers no way to type a repository address or an arbitrary path", async () => {
    stubApi((url) => {
      if (url.endsWith("/projects")) return json(noProjects);
      if (url.endsWith("/github/repositories")) {
        return json({
          items: [
            { githubRepositoryId: "101", fullName: "UncleSamsun/syncdoc", isPrivate: false, defaultBranch: "main" },
          ],
          complete: true,
        });
      }
      if (url.includes("/branches")) {
        return json({ items: [{ name: "main", isDefault: true }, { name: "dev", isDefault: false }] });
      }
      throw new Error("unexpected " + url);
    });

    render(<ProjectHomePage csrfToken="c1" />);

    await waitFor(() => expect(screen.getByLabelText("저장소")).toBeInTheDocument());
    // 저장소와 브랜치는 고르는 칸이고, 자유 입력은 문서 경로 하나뿐이다.
    expect(screen.getByLabelText("저장소").tagName).toBe("SELECT");
    expect(screen.getByLabelText("기준 브랜치").tagName).toBe("SELECT");
    expect(screen.getAllByRole("textbox")).toHaveLength(1);
    expect(screen.getByLabelText("문서 경로")).toHaveValue("docs");
  });

  it("prefills main and docs as the defaults", async () => {
    stubApi((url) => {
      if (url.endsWith("/projects")) return json(noProjects);
      if (url.endsWith("/github/repositories")) {
        return json({
          items: [{ githubRepositoryId: "101", fullName: "o/r", isPrivate: false, defaultBranch: "trunk" }],
          complete: true,
        });
      }
      if (url.includes("/branches")) {
        return json({ items: [{ name: "trunk", isDefault: true }, { name: "main", isDefault: false }] });
      }
      throw new Error("unexpected " + url);
    });

    render(<ProjectHomePage csrfToken="c1" />);
    await waitFor(() => expect(screen.getByLabelText("저장소")).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText("저장소"), "101");

    await waitFor(() => expect(screen.getByLabelText("기준 브랜치")).toHaveValue("main"));
    expect(screen.getByLabelText("문서 경로")).toHaveValue("docs");
  });

  it("shows the missing-path message under the field instead of a generic error", async () => {
    stubApi((url, init) => {
      if (url.endsWith("/projects") && init?.method === "POST") {
        return json(
          {
            code: "INVALID_REQUEST",
            message: "이 브랜치에 spec 경로가 없습니다. 실제 경로를 선택하세요.",
            requestId: "r1",
            details: { field: "docsRoot" },
          },
          422,
        );
      }
      if (url.endsWith("/projects")) return json(noProjects);
      if (url.endsWith("/github/repositories")) {
        return json({
          items: [{ githubRepositoryId: "101", fullName: "o/r", isPrivate: false, defaultBranch: "main" }],
          complete: true,
        });
      }
      if (url.includes("/branches")) return json({ items: [{ name: "main", isDefault: true }] });
      throw new Error("unexpected " + url);
    });

    render(<ProjectHomePage csrfToken="c1" />);
    await waitFor(() => expect(screen.getByLabelText("저장소")).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText("저장소"), "101");
    await userEvent.click(screen.getByRole("button", { name: "연결" }));

    await waitFor(() =>
      expect(
        screen.getByText("이 브랜치에 spec 경로가 없습니다. 실제 경로를 선택하세요."),
      ).toBeInTheDocument(),
    );
  });

  it("treats a duplicate connection as already connected, not as an error", async () => {
    const existing = {
      id: "p1",
      githubRepositoryId: "101",
      fullName: "o/r",
      branch: "main",
      docsRoot: "docs",
      githubProjectNodeId: null,
      currentSnapshotId: null,
      syncState: "queued",
      version: 0,
      manageable: true,
    };
    stubApi((url, init) => {
      if (url.endsWith("/projects") && init?.method === "POST") {
        return json(
          {
            code: "REPOSITORY_ALREADY_CONNECTED",
            message: "이미 연결된 저장소입니다.",
            requestId: "r1",
            details: { projectId: "p1" },
          },
          409,
        );
      }
      if (url.endsWith("/projects")) return json({ items: [existing] });
      if (url.endsWith("/github/repositories")) {
        return json({
          items: [{ githubRepositoryId: "101", fullName: "o/r", isPrivate: false, defaultBranch: "main" }],
          complete: true,
        });
      }
      if (url.includes("/branches")) return json({ items: [{ name: "main", isDefault: true }] });
      throw new Error("unexpected " + url);
    });

    render(<ProjectHomePage csrfToken="c1" />);
    await waitFor(() => expect(screen.getByLabelText("저장소")).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText("저장소"), "101");
    await userEvent.click(screen.getByRole("button", { name: "연결" }));

    await waitFor(() => expect(screen.getByText("이미 연결됨")).toBeInTheDocument());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "열기" })).toHaveAttribute("href", "/projects/p1");
  });

  it("shows a freshly connected project as waiting for its first sync", async () => {
    stubApi((url) => {
      if (url.endsWith("/projects")) {
        return json({
          items: [
            {
              id: "p2",
              githubRepositoryId: "101",
              fullName: "o/r",
              branch: "main",
              docsRoot: "docs",
              githubProjectNodeId: null,
              currentSnapshotId: null,
              syncState: "queued",
              version: 0,
              manageable: true,
            },
          ],
        });
      }
      if (url.endsWith("/github/repositories")) return json({ items: [], complete: true });
      throw new Error("unexpected " + url);
    });

    render(<ProjectHomePage csrfToken="c1" />);

    await waitFor(() => expect(screen.getByText("첫 수집 대기")).toBeInTheDocument());
  });

  it("says GitHub could not be checked instead of showing an empty list as the truth", async () => {
    stubApi((url) => {
      if (url.endsWith("/projects")) {
        return json({ code: "GITHUB_UNAVAILABLE", message: "", requestId: "r", details: {} }, 503);
      }
      if (url.endsWith("/github/repositories")) return json({ items: [], complete: true });
      throw new Error("unexpected " + url);
    });

    render(<ProjectHomePage csrfToken="c1" />);

    await waitFor(() =>
      expect(screen.getByText("GitHub 권한을 확인할 수 없습니다.")).toBeInTheDocument(),
    );
  });
});
