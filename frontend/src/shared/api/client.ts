export const API_BASE = "/api/v1";

/** API 계약 `## 공통`의 오류 형식. */
export type ApiErrorBody = {
  code: string;
  message: string;
  requestId: string;
  details: Record<string, unknown>;
};

export type ApiError = ApiErrorBody & { status: number };

export function isApiError(value: unknown): value is ApiError {
  return typeof value === "object" && value !== null && "status" in value && "code" in value;
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });
  if (response.ok) {
    return (await response.json()) as T;
  }
  let body: Partial<ApiErrorBody> = {};
  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    body = {};
  }
  const error: ApiError = {
    status: response.status,
    code: body.code ?? "UNKNOWN",
    message: body.message ?? response.statusText,
    requestId: body.requestId ?? "",
    details: body.details ?? {},
  };
  throw error;
}

/** 상태를 바꾸는 요청. API-003이 준 CSRF 토큰을 헤더로 함께 보낸다. 204는 본문 없이 성공이다. */
export async function apiPost<T>(path: string, body: unknown, csrfToken: string): Promise<T | null> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      "X-CSRF-Token": csrfToken,
    },
    body: JSON.stringify(body),
  });
  if (response.status === 204) {
    return null;
  }
  if (response.ok) {
    return (await response.json()) as T;
  }
  let parsed: Partial<ApiErrorBody> = {};
  try {
    parsed = (await response.json()) as ApiErrorBody;
  } catch {
    parsed = {};
  }
  const error: ApiError = {
    status: response.status,
    code: parsed.code ?? "UNKNOWN",
    message: parsed.message ?? response.statusText,
    requestId: parsed.requestId ?? "",
    details: parsed.details ?? {},
  };
  throw error;
}
