import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "../src/app/App";

describe("App", () => {
  it("renders the service name and the not-connected notice", () => {
    render(<App />);
    expect(screen.getByRole("heading", { name: "SyncDoc" })).toBeInTheDocument();
    expect(screen.getByText("서버에 연결되지 않았습니다.")).toBeInTheDocument();
  });
});
