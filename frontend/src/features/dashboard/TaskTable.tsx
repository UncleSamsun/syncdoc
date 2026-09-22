import { Link } from "react-router-dom";
import TaskRows from "./TaskRows";
import type { TaskView } from "./types";

type Props = {
  projectId: string;
  tasks: TaskView[];
  total: number;
  unmatched: string[];
};

/**
 * UI-002의 작업 · Issue 매핑 표.
 *
 * <p>여기는 현황이 싣는 첫 20건이다. 나머지는 UI-014에서 이어 본다. 표시 규칙은 {@link TaskRows}가
 * 들고 있으며 화면마다 다시 정하지 않는다.
 */
export default function TaskTable({ projectId, tasks, total, unmatched }: Props) {
  return (
    <section className="panel" id="tasks">
      <h2>
        작업 · Issue
        <span className="n">
          {tasks.length}건 중 {total}건
        </span>
        {/* 여기서 잘린 나머지를 볼 곳을 함께 둔다. 건수만 알리고 막다른 길로 두지 않는다. */}
        <Link className="n" to={`/projects/${projectId}/tasks`}>
          전체 보기
        </Link>
      </h2>
      <TaskRows projectId={projectId} tasks={tasks} />
      {unmatched.length > 0 && (
        <p className="n">
          명세에 없는 작업 ID를 주장하는 Issue: {unmatched.join(", ")} · 완료율 분모에는 넣지 않습니다.
        </p>
      )}
      <p className="n">배포 상태는 미수집입니다. dev 개발 완료와 main 릴리스는 다른 값입니다.</p>
    </section>
  );
}
