/** API-015·API-016이 쓰는 작업 상태. */
export type TaskStatus =
  | "unregistered"
  | "in_progress"
  | "done"
  | "canceled"
  | "mapping_conflict";

export type TaskView = {
  taskSpecId: string;
  title: string;
  documentId: string;
  anchor: string;
  /** Issue가 없으면 null이다. 화면은 `—`로 표시한다. */
  issueNumber: number | null;
  issueTitle: string | null;
  status: TaskStatus;
  assignees: string[];
  pullRequests: number[];
  mappingConflict: boolean;
  observedAt: string | null;
};

export type Counts = {
  notStarted: number;
  inProgress: number;
  inReview: number;
  done: number;
  unregistered: number;
  canceled: number;
  total: number;
};

/** 분모가 0이면 `ratio`가 null이다. 0%가 아니라 계산 대상 없음이라는 뜻이다. */
export type Progress = {
  completed: number;
  denominator: number;
  ratio: number | null;
};

export type RecentChange = {
  documentId: string;
  path: string;
  title: string;
  change: "added" | "modified";
};

/** `available`·`unavailable`·`not_connected`. 연결 안 함과 권한 없음은 다른 상태다. */
export type ProjectAccess = "available" | "unavailable" | "not_connected";

export type OverviewView = {
  snapshotId: string | null;
  sourceRevision: string | null;
  repositoryObservedAt: string | null;
  projectObservedAt: string | null;
  projectAccess: ProjectAccess;
  counts: Counts;
  progress: Progress;
  projectProgress: Record<string, number> | null;
  documentCount: number;
  recentChanges: RecentChange[];
  tasks: TaskView[];
  taskTotal: number;
  unmatchedIssueTasks: string[];
  /** 원천별 불완전 여부. 하나라도 참이면 숫자를 확정된 값으로 보여주지 않는다. */
  partial: Record<string, boolean>;
};

export type SearchHit = {
  documentId: string;
  path: string;
  title: string;
  anchor: string | null;
  excerpt: string;
};

export type SearchResults = {
  snapshotId: string | null;
  query: string;
  items: SearchHit[];
  total: number;
};

export function isPartial(partial: Record<string, boolean> | undefined): boolean {
  return Boolean(partial && Object.values(partial).some(Boolean));
}

/** 상태별 한글 이름. 색만으로 상태를 전달하지 않기 위해 어디서든 글자를 함께 쓴다. */
export const STATUS_LABELS: Record<TaskStatus, string> = {
  unregistered: "Issue 미등록",
  in_progress: "진행",
  done: "완료",
  canceled: "취소",
  mapping_conflict: "연결 충돌",
};
