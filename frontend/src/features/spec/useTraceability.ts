import { useEffect, useState } from "react";
import { apiGet, isApiError } from "../../shared/api/client";
import type { Coverage, TraceView, TraceFindings } from "./traceabilityTypes";
type Result = { state: "ready"; view: TraceView; findings: TraceFindings } |
  { state: "loading" | "waiting" | "missing" | "gone" | "unavailable" };
export function useTraceability(projectId: string, snapshotId: string, coverage: Coverage, page: number, findingPage: number) {
  const key = JSON.stringify([projectId,snapshotId,coverage,page,findingPage]);
  const [result,setResult] = useState<{key:string; value:Result}>();
  useEffect(()=>{
    let active = true;
    const controller = new AbortController();
    const base = `/projects/${projectId}/spec-traceability`;
    const pinned = `snapshotId=${encodeURIComponent(snapshotId)}&size=50`;
    Promise.all([
      apiGet<TraceView>(`${base}?${pinned}&coverage=${coverage}&page=${page}`,controller.signal),
      apiGet<TraceFindings>(`${base}/findings?${pinned}&page=${findingPage}`,controller.signal),
    ]).then(([view,findings])=>{
      if (!active) return;
      if (view.snapshotId !== snapshotId || findings.snapshotId !== snapshotId) throw new Error("Snapshot mismatch");
      setResult({key,value:{state:"ready",view,findings}});
    }).catch(error=>{
      if (!active) return;
      const state = isApiError(error) ? ({409:"waiting",404:"missing",410:"gone"} as const)[error.status as 409|404|410] ?? "unavailable" : "unavailable";
      setResult({key,value:{state}});
    });
    return ()=>{ active=false; controller.abort(); };
  },[projectId,snapshotId,coverage,page,findingPage,key]);
  return result?.key === key ? result.value : {state:"loading" as const};
}
