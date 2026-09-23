import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import LoginPage from "../src/features/auth/LoginPage";
import UninvitedPage from "../src/features/auth/UninvitedPage";

describe("UI-005 로그인", () => {
  it("shows the invite-only notice and a single GitHub entry point", () => {
    render(<LoginPage />);
    expect(screen.getByText("초대받은 GitHub 계정으로 로그인하세요.")).toBeInTheDocument();
    expect(screen.getAllByRole("link")).toHaveLength(1);
    expect(screen.getByRole("link", { name: "GitHub로 계속" })).toHaveAttribute(
      "href",
      "/api/v1/auth/github/start",
    );
  });

  it("keeps the requested path so login can return to it", () => {
    render(<LoginPage returnTo="/projects/x" />);
    expect(screen.getByRole("link", { name: "GitHub로 계속" })).toHaveAttribute(
      "href",
      "/api/v1/auth/github/start?returnTo=%2Fprojects%2Fx",
    );
  });

  it("explains a failed state check without leaving the screen", () => {
    render(<LoginPage error="state" />);
    expect(screen.getByText("로그인을 다시 시도해 주세요.")).toBeInTheDocument();
  });
});

describe("UI-006 미초대 계정", () => {
  it("says the account is not invited and offers no way further in", () => {
    render(<UninvitedPage />);
    expect(screen.getByRole("heading", { name: "이 계정은 아직 초대되지 않았습니다" })).toBeInTheDocument();
    expect(screen.getAllByRole("link")).toHaveLength(1);
    expect(screen.getByRole("link", { name: "로그아웃" })).toHaveAttribute("href", "/login");
  });

  it("does not mention projects at all", () => {
    const { container } = render(<UninvitedPage />);
    expect(container.textContent).not.toContain("프로젝트");
  });
});
