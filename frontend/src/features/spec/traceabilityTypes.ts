import type { TaskView } from "../dashboard/types";
export type TraceItem = { itemId: string; title: string; documentId: string; path: string; anchor: string; line: number };
export type Coverage = "all" | "linked" | "unlinked" | "unknown";
export type TraceMeta = { snapshotId: string; sourceRevision: string; analysisVersion: number | null;
  analysisStatus: "complete" | "partial" | "unchecked"; uncheckedReason: string | null; totalElements: number; page: number; size: number };
export type TraceView = TraceMeta & { requirements: { item: TraceItem; coverage: Exclude<Coverage,"all">;
  tasks: { item: TraceItem; execution: TaskView | null }[] }[] };
export type TraceFindings = TraceMeta & { findings: { code: string; severity: string; documentId: string;
  path: string; line: number; itemId: string | null; targetId: string | null; message: string }[] };
