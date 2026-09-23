import { Link } from "react-router-dom";
import type { TaskView } from "./types";
import { STATUS_LABELS } from "./types";

const TONE: Record<TaskView["status"], string> = {
  done: "ok",
  in_progress: "prog",
  unregistered: "todo",
  canceled: "off",
  mapping_conflict: "fail",
};

/**
 * 작업 표. UI-002의 작업 표 규칙이 여기 한 곳에 있다.
 *
 * <p>현황(UI-002)과 작업 전체 목록(UI-014)이 같은 표를 쓴다. 같은 자료를 두 화면이 다르게
 * 보여주면 어느 쪽이 맞는지 알 수 없다.
 *
 * <p>Issue가 없는 작업이 목록에서 사라지지 않는다. 취소는 완료와 다른 표시를 쓰고 분모 제외를
 * 밝힌다. PR이 여럿이면 번호를 모두 적는다 — PR 개수를 완료 건수로 읽히게 두지 않는다.
 */
export default function TaskRows({ projectId, tasks }: { projectId: string; tasks: TaskView[] }) {
  return (
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
  );
}
