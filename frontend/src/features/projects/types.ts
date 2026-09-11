/** API-008의 후보 저장소. 임의 URL이나 로컬 경로는 후보가 될 수 없다. */
export type RepositoryItem = {
  githubRepositoryId: string;
  fullName: string;
  isPrivate: boolean;
  defaultBranch: string;
};

/** API-024의 브랜치. 이름을 직접 입력하지 않고 이 목록에서 고른다. */
export type BranchItem = {
  name: string;
  isDefault: boolean;
};

/** 수집 상태. API-010·API-014가 같은 값을 쓴다. */
export type SyncState = "queued" | "running" | "succeeded" | "failed";

/** API-010·API-011이 돌려주는 프로젝트. */
export type ProjectItem = {
  id: string;
  githubRepositoryId: string;
  fullName: string;
  branch: string;
  docsRoot: string;
  githubProjectNodeId: string | null;
  currentSnapshotId: string | null;
  syncState: SyncState;
  /** 마지막으로 성공한 수집 시각. 한 번도 성공하지 않았으면 null이다. */
  lastSuccessAt: string | null;
  syncErrorCode: string | null;
  /** 현재 게시본의 문서 수. 게시본이 없으면 null이며 0으로 대체하지 않는다. */
  documentCount: number | null;
  version: number;
  manageable: boolean;
};

/** API-014의 수집 상태. */
export type SyncStatus = {
  state: SyncState;
  lastAttemptAt: string | null;
  lastSuccessAt: string | null;
  errorCode: string | null;
  nextRetryAt: string | null;
  pending: boolean;
};

export const DEFAULT_BRANCH = "main";
export const DEFAULT_DOCS_ROOT = "docs";
