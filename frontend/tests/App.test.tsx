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

  it("greets the signed-in account by login", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
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
      ),
    );
    render(<App />);
    await waitFor(() => expect(screen.getByText("octocat 님으로 로그인했습니다.")).toBeInTheDocument());
  });
});
