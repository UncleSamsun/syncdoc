import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "../src/app/App";

describe("App", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("shows the login screen when there is no session", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: "UNAUTHENTICATED", message: "", requestId: "", details: {} }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );
    render(<App />);
    await waitFor(() =>
      expect(screen.getByText("초대받은 GitHub 계정으로 로그인하세요.")).toBeInTheDocument(),
    );
  });

  it("lands on the project home once signed in", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (String(url).endsWith("/me")) {
          return Promise.resolve(
            new Response(
              JSON.stringify({
                id: "u1",
                githubUserId: "583231",
                login: "octocat",
                serviceAdmin: false,
                csrfToken: "c1",
              }),
              { status: 200, headers: { "Content-Type": "application/json" } },
            ),
          );
        }
        return Promise.resolve(
          new Response(JSON.stringify({ items: [], complete: true }), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          }),
        );
      }),
    );
    render(<App />);
    await waitFor(() =>
      expect(screen.getByRole("heading", { name: "프로젝트 홈" })).toBeInTheDocument(),
    );
  });
});
