import { describe, expect, it } from "vitest";
import { DIAGRAM_CONFIG } from "../src/features/documents/DiagramView";

describe("trusted dark diagram configuration", () => {
  it("keeps repository directives from overriding theme and security", () => {
    expect(DIAGRAM_CONFIG.securityLevel).toBe("strict");
    expect(DIAGRAM_CONFIG.htmlLabels).toBe(false);
    expect(DIAGRAM_CONFIG.suppressErrorRendering).toBe(true);
    for (const key of ["securityLevel", "htmlLabels", "theme", "themeVariables", "themeCSS", "fontFamily"]) {
      expect(DIAGRAM_CONFIG.secure).toContain(key);
    }
    expect(DIAGRAM_CONFIG.themeVariables.darkMode).toBe(true);
  });
});
