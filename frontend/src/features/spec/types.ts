/** API-025의 상태. 검증 규칙 §7의 통과·오류·미작성·미검사와 같다. */
export type ChecklistStatus = "pass" | "error" | "pending" | "unchecked";

/** 규칙 파일을 읽지 못한 이유. `unchecked`일 때만 온다. */
export type UncheckedReason =
  | "DEFINITION_MISSING"
  | "APPLY_TABLE_MISSING"
  | "NO_DOCUMENTS"
  | "NOT_COMPUTED";

export type ChecklistFinding = {
  /** 없는 문서나 적용 Spec 표의 오류면 null이다. */
  documentId: string | null;
  path: string | null;
  line: number | null;
  check: "C1" | "C2";
  message: string;
};

export type ChecklistDocument = {
  documentId: string;
  path: string;
  specId: string | null;
};

export type ChecklistType = {
  type: string;
  name: string;
  apply: string;
  reason: string;
  /** `미적용` 종류는 검사하지 않았으므로 null이다. 통과로 보이게 하지 않는다. */
  status: ChecklistStatus | null;
  documents: ChecklistDocument[];
  findings: ChecklistFinding[];
};

/** API-025의 본문. */
export type ChecklistView = {
  snapshotId: string;
  sourceRevision: string;
  status: ChecklistStatus;
  uncheckedReason: UncheckedReason | null;
  truncated: boolean;
  /** 어느 종류에도 속하지 않는 오류. 문서 식별·분류와 적용 Spec 표 자체의 오류다. */
  findings: ChecklistFinding[];
  types: ChecklistType[];
};

/** 종류별 오류와 종류에 붙지 않는 오류를 모두 센다. */
export function errorCountOf(checklist: ChecklistView): number {
  return checklist.findings.length
    + checklist.types.reduce((total, type) => total + type.findings.length, 0);
}
