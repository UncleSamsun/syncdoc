import { afterEach, describe, expect, it, vi } from "vitest";
import { apiGet } from "../src/shared/api/client";

describe("apiGet", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("prefixes /api/v1 and returns parsed JSON", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ status: "UP" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);
    await expect(apiGet<{ status: string }>("/health/live")).resolves.toEqual({ status: "UP" });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/health/live",
      expect.objectContaining({ credentials: "same-origin" }),
    );
  });

  it("throws the contract error body on non-2xx", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ code: "UNAUTHENTICATED", message: "로그인이 필요합니다.", requestId: "r1", details: {} }),
          { status: 401, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );
    await expect(apiGet("/me")).rejects.toMatchObject({ status: 401, code: "UNAUTHENTICATED", requestId: "r1" });
  });
});
