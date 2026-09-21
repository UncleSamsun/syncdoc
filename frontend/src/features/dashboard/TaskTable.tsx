import { Link } from "react-router-dom";
import type { TaskView } from "./types";
import { STATUS_LABELS } from "./types";

type Props = {
  projectId: string;
  tasks: TaskView[];
  total: number;
  unmatched: string[];
};

const TONE: Record<TaskView["status"], string> = {
  done: "ok",
  in_progress: "prog",
  unregistered: "todo",
  canceled: "off",
  mapping_conflict: "fail",
};

/**
 * UI-002의 작업 · Issue 매핑 표.
 *
 * <p>Issue가 없는 작업이 목록에서 사라지지 않는다. 취소는 완료와 다른 표시를 쓰고 분모 제외를 밝힌다.
 * PR이 여럿이면 번호를 모두 적는다 — PR 개수를 완료 건수로 읽히게 두지 않는다.
 */
export default function TaskTable({ projectId, tasks, total, unmatched }: Props) {
  return (
    <section className="panel" id="tasks">
      <h2>
        작업 · Issue
        <span className="n">
          {tasks.length}건 중 {total}건
        </span>
      </h2>
      <div className="tblwrap">
        <table className="mdtbl tasks">
          <thead>
            <tr>
              <th>TASK</th>
              <th>제목</th>
              <th>Issue</th>
              <th>상태</th>
              <th>PR</th>
              <th>담당</th>
            </tr>
          </thead>
          <tbody>
            {tasks.map((task) => (
              <tr key={task.taskSpecId} className={task.status === "canceled" ? "row--canceled" : ""}>
                <td className="mono">{task.taskSpecId}</td>
                <td>
                  <Link to={`/projects/${projectId}/documents/${task.documentId}#${task.anchor}`}>
                    {task.title}
                  </Link>
                </td>
                <td className="mono">{task.issueNumber ? `#${task.issueNumber}` : "—"}</td>
                <td>
                  {task.status === "unregistered" ? (
                    <span className="chip chip--dashed">Issue 미등록</span>
                  ) : task.status === "canceled" ? (
                    <span className="chip chip--off">취소 · 제외</span>
                  ) : (
                    <span className={`st st--${TONE[task.status]}`}>
                      <span className="dot" aria-hidden="true" />
                      {STATUS_LABELS[task.status]}
                    </span>
                  )}
                </td>
                <td className="mono">
                  {task.pullRequests.length === 0
                    ? "—"
                    : task.pullRequests.map((number) => `#${number}`).join(" ")}
                </td>
                <td>{task.assignees.length === 0 ? "미배정" : task.assignees.join(", ")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {unmatched.length > 0 && (
        <p className="n">
          명세에 없는 작업 ID를 주장하는 Issue: {unmatched.join(", ")} · 완료율 분모에는 넣지 않습니다.
        </p>
      )}
      <p className="n">배포 상태는 미수집입니다. dev 개발 완료와 main 릴리스는 다른 값입니다.</p>
    </section>
  );
}
