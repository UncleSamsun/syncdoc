import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import ResizableShellBody from "../src/features/shell/ResizableShellBody";

const KEY = "syncdoc:sidebar-width";
function open() {
  return render(<ResizableShellBody sidebar={<nav id="project-sidebar">문서</nav>}><main>본문</main></ResizableShellBody>);
}
beforeEach(() => localStorage.removeItem(KEY));
afterEach(() => vi.unstubAllGlobals());

it("resizes by dragging the boundary and remembers the released width", () => {
  class Pointer extends MouseEvent {
    readonly pointerId: number;
    constructor(type: string, options: PointerEventInit) { super(type, options); this.pointerId = options.pointerId ?? 1; }
  }
  vi.stubGlobal("PointerEvent", Pointer);
  open();
  const handle = screen.getByRole("separator", { name: "사이드바 너비 조절" });
  fireEvent.pointerDown(handle, { button: 0, pointerId: 1, clientX: 248 });
  fireEvent.pointerMove(handle, { pointerId: 1, clientX: 420 });
  expect(handle).toHaveAttribute("aria-valuenow", "420");
  fireEvent.pointerUp(handle, { pointerId: 1, clientX: 420 });
  expect(localStorage.getItem(KEY)).toBe("420");
});

it("supports keyboard bounds, restore, and double-click reset", async () => {
  const first = open();
  const handle = screen.getByRole("separator");
  await userEvent.type(handle, "{ArrowRight}");
  expect(handle).toHaveAttribute("aria-valuenow", "264");
  first.unmount(); open();
  expect(screen.getByRole("separator")).toHaveAttribute("aria-valuenow", "264");
  fireEvent.keyDown(screen.getByRole("separator"), { key: "End" });
  expect(screen.getByRole("separator")).toHaveAttribute("aria-valuenow", "560");
  fireEvent.keyDown(screen.getByRole("separator"), { key: "Home" });
  expect(screen.getByRole("separator")).toHaveAttribute("aria-valuenow", "200");
  fireEvent.doubleClick(screen.getByRole("separator"));
  expect(screen.getByRole("separator")).toHaveAttribute("aria-valuenow", "248");
});

it("ignores invalid saved widths and keeps room for the document on smaller desktops", () => {
  localStorage.setItem(KEY, "broken");
  vi.stubGlobal("innerWidth", 800);
  open();
  expect(screen.getByRole("separator")).toHaveAttribute("aria-valuenow", "248");
  fireEvent.keyDown(screen.getByRole("separator"), { key: "End" });
  expect(screen.getByRole("separator")).toHaveAttribute("aria-valuenow", "480");
});
