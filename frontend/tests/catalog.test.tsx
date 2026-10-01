import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import DocumentCatalog from "../src/features/documents/DocumentCatalog";
import type { ChecklistType } from "../src/features/spec/types";

const type: ChecklistType = { type: "custom-design", name: "팀 설계 기준", apply: "적용",
  reason: "", status: "pass", documents: [], findings: [] };

describe("project-defined document catalog", () => {
  it("uses the repository's type name instead of guessing from its folder", () => {
    render(<MemoryRouter><DocumentCatalog projectId="p1" types={[type]} documents={[
      { id: "d1", path: "arbitrary/anywhere.md", title: "시스템 설계", kind: "custom-design" },
    ]} currentDocumentId="d1" /></MemoryRouter>);
    expect(screen.getByText("팀 설계 기준")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "시스템 설계" })).toHaveAttribute("aria-current", "page");
  });
  it("keeps unknown documents reachable without inventing a classification", () => {
    render(<MemoryRouter><DocumentCatalog projectId="p1" types={[type]} documents={[
      { id: "d2", path: "01-prd/not-a-prd.md", title: "이름만 PRD인 문서", kind: null },
    ]} /></MemoryRouter>);
    expect(screen.getByText("분류 없음")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "이름만 PRD인 문서" })).toHaveAttribute("href", "/projects/p1/documents/d2");
    expect(screen.queryByText("팀 설계 기준")).not.toBeInTheDocument();
  });
  it("keeps the historical snapshot when the historical taxonomy is available", () => {
    render(<MemoryRouter><DocumentCatalog projectId="p1" snapshotId="old snapshot" types={[type]} documents={[
      { id: "old", path: "docs/old.md", title: "과거 설계", kind: "custom-design" },
    ]} /></MemoryRouter>);
    expect(screen.getByRole("link", { name: "과거 설계" })).toHaveAttribute("href", "/projects/p1/documents/old?snapshotId=old%20snapshot");
  });
});
