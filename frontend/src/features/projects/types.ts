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

/** API-010·API-011이 돌려주는 프로젝트. */
export type ProjectItem = {
  id: string;
  githubRepositoryId: string;
  fullName: string;
  branch: string;
  docsRoot: string;
  githubProjectNodeId: string | null;
  currentSnapshotId: string | null;
  syncState: "queued" | "ready";
  version: number;
  manageable: boolean;
};

export const DEFAULT_BRANCH = "main";
export const DEFAULT_DOCS_ROOT = "docs";
