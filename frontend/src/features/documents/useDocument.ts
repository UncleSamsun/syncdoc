import { useCallback, useEffect, useState } from "react";
import { apiGet, isApiError } from "../../shared/api/client";
import type { DocumentList, DocumentView } from "./types";

/**
 * 화면이 구분해야 하는 문서 상태.
 *
 * `waiting`은 첫 수집 전(UI-007), `gone`은 회수된 게시본(UI-012), `missing`은 없거나 권한이
 * 없는 문서(UI-011), `unreadable`은 변환 결과가 없는 문서다. 하나로 뭉치면 화면이 잘못된 안내를 한다.
 */
export type DocumentState =
  | { state: "loading" }
  | { state: "ready"; document: DocumentView }
  | { state: "waiting" }
  | { state: "missing" }
  | { state: "gone" }
  | { state: "unreadable" }
  | { state: "unavailable" };

export type DocumentListState =
  | { state: "loading" }
  | { state: "ready"; list: DocumentList }
  | { state: "missing" }
  | { state: "gone" }
  | { state: "unavailable" };

function withSnapshot(path: string, snapshotId: string | null | undefined): string {
  return snapshotId ? `${path}${path.includes("?") ? "&" : "?"}snapshotId=${snapshotId}` : path;
}

/** API-017. 목록이 비어 있는 것은 오류가 아니다. */
export function useDocumentList(projectId: string | undefined, snapshotId?: string | null) {
  const [list, setList] = useState<DocumentListState>({ state: "loading" });

  const reload = useCallback(async () => {
    if (!projectId) {
      return;
    }
    try {
      const loaded = await apiGet<DocumentList>(
        withSnapshot(`/projects/${projectId}/documents?limit=100`, snapshotId),
      );
      setList({ state: "ready", list: loaded });
    } catch (error) {
      const status = statusOf(error);
      // 목록은 첫 수집 전에도 200이다. 여기서 409나 422가 오는 경우는 없다.
      setList({ state: status === "gone" ? "gone" : status === "missing" ? "missing" : "unavailable" });
    }
  }, [projectId, snapshotId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { list, reload };
}

/** API-018. */
export function useDocument(
  projectId: string | undefined,
  documentId: string | undefined,
  snapshotId?: string | null,
) {
  const [state, setState] = useState<DocumentState>({ state: "loading" });

  useEffect(() => {
    if (!projectId || !documentId) {
      return;
    }
    let cancelled = false;
    setState({ state: "loading" });
    apiGet<DocumentView>(withSnapshot(`/projects/${projectId}/documents/${documentId}`, snapshotId))
      .then((document) => {
        if (!cancelled) {
          setState({ state: "ready", document });
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setState({ state: statusOf(error) });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [projectId, documentId, snapshotId]);

  return state;
}

function statusOf(error: unknown): "waiting" | "missing" | "gone" | "unreadable" | "unavailable" {
  if (!isApiError(error)) {
    return "unavailable";
  }
  switch (error.status) {
    case 409:
      return "waiting";
    case 410:
      return "gone";
    case 404:
      return "missing";
    case 422:
      return "unreadable";
    default:
      return "unavailable";
  }
}
