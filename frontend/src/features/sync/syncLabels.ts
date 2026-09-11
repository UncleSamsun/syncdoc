import type { ProjectItem, SyncState } from "../projects/types";

/**
 * 목록에 붙는 수집 상태 라벨. 공통 UI 규칙에 따라 색만으로 상태를 전달하지 않고 글자를 함께 붙인다.
 *
 * <p>대기와 없음을 같은 문구로 쓰지 않는다. 게시본이 아직 없는 대기는 `첫 수집 대기`이고,
 * 이미 게시본이 있는 대기는 `갱신 대기`다. 둘을 합치면 처음 연결한 프로젝트가 문서 없는
 * 프로젝트처럼 보인다.
 */
export function syncLabelOf(project: Pick<ProjectItem, "syncState" | "currentSnapshotId">): string {
  switch (project.syncState) {
    case "running":
      return "수집 중";
    case "failed":
      return "갱신 실패";
    case "succeeded":
      return "최신";
    default:
      return project.currentSnapshotId ? "갱신 대기" : "첫 수집 대기";
  }
}

/** 상태 점의 의미색을 고르는 값. 실패만 실패 빨강이고 수집 중은 주의 주황이다. */
export function syncToneOf(state: SyncState): "ok" | "fail" | "review" | "todo" {
  switch (state) {
    case "failed":
      return "fail";
    case "running":
      return "review";
    case "succeeded":
      return "ok";
    default:
      return "todo";
  }
}

/** 시각은 고정폭으로 보이는 식별 정보다. 초는 버리고 분까지만 보여 준다. */
export function formatMoment(value: string | null): string | null {
  if (!value) {
    return null;
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  const pad = (part: number) => String(part).padStart(2, "0");
  return (
    `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())} ` +
    `${pad(parsed.getHours())}:${pad(parsed.getMinutes())}`
  );
}
