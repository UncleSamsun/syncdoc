import { useCallback, useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { apiGet, isApiError } from "../../shared/api/client";
import { AccessUnavailable, DocumentMissing } from "../documents/DocumentStates";
import { useDocumentList } from "../documents/useDocument";
import type { ProjectItem } from "../projects/types";
import AppShell from "../shell/AppShell";
import { errorCountOf } from "../spec/types";
import { useChecklist } from "../spec/useChecklist";
import { FirstSyncWaiting } from "../sync/SyncStates";
import { useSnapshotWatch } from "../sync/useSnapshotWatch";
import TaskRows from "./TaskRows";
import type { TaskStatus, TaskView } from "./types";
import { STATUS_LABELS } from "./types";

type TaskList = {
  snapshotId: string | null;
  items: TaskView[];
  total: number;
  nextCursor: string | null;
};

const STATUS_ORDER: TaskStatus[] = [
  "in_progress",
  "done",
  "unregistered",
  "canceled",
  "mapping_conflict",
];

/**
 * UI-014 작업 전체 목록.
 *
 * <p>현황의 작업 표는 첫 20건이다. 20건을 넘는 프로젝트에서 나머지를 볼 곳이 여기다. 표시 규칙은
 * {@link TaskRows}가 들고 있어 두 화면이 같은 자료를 다르게 보여주지 않는다.
 *
 * <p>거르기는 주소에 남는다. 새로 고쳐도 같은 목록이고 그 주소를 그대로 전달할 수 있다.
 */
export default function TaskListPage() {
  const { projectId } = useParams();
  const [params, setParams] = useSearchParams();
  const status = params.get("status") ?? "";
  const assignee = params.get("assignee") ?? "";

  const [project, setProject] = useState<ProjectItem | null>(null);
  const [all, setAll] = useState<TaskList | null>(null);
  const [failure, setFailure] = useState<"missing" | "unavailable" | undefined>();
  const { list } = useDocumentList(projectId);
  const checklist = useChecklist(projectId);

  const load = useCallback(() => {
    if (!projectId) {
      return;
    }
    apiGet<ProjectItem>(`/projects/${projectId}`)
      .then(setProject)
      .catch((error) =>
        setFailure(isApiError(error) && error.status === 404 ? "missing" : "unavailable"),
      );
    // 거르기는 화면에서 한다. 담당 후보를 목록에 실제로 있는 사람만으로 채우려면 전체가 필요하다.
    apiGet<TaskList>(`/projects/${projectId}/tasks`)
      .then(setAll)
      .catch(() => setFailure("unavailable"));
  }, [projectId]);

  useEffect(load, [load]);
  // 새 게시본이 나오면 조용히 다시 읽는다. 거른 조건과 읽던 자리를 잃지 않는다.
  useSnapshotWatch(projectId, load);

  if (failure === "missing") {
    return <DocumentMissing projectId={projectId ?? ""} />;
  }
  if (failure === "unavailable") {
    return <AccessUnavailable />;
  }
  if (!project || !all) {
    return <main className="centered">불러오는 중입니다.</main>;
  }

  const documents = list.state === "ready" ? list.list.items : [];
  const assignees = [...new Set(all.items.flatMap((task) => task.assignees))].sort();
  const shown = all.items
    .filter((task) => !status || task.status === status)
    .filter((task) => !assignee || task.assignees.includes(assignee));
  const filtered = Boolean(status || assignee);

  const change = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    setParams(next);
  };

  return (
    <AppShell
      project={project}
      documents={documents}
      taskCount={all.total}
      checklistErrors={checklist.state === "ready" ? errorCountOf(checklist.checklist) : undefined}
      active="tasks"
    >
      {all.snapshotId === null ? (
        <FirstSyncWaiting />
      ) : (
        <section className="tasklist">
          <header className="chk-h">
            <h1>작업</h1>
            <span className="mono">{all.snapshotId.slice(0, 8)}</span>
            <span className="n">
              {/* 거른 탓에 줄어든 것을 전체가 줄어든 것으로 읽히게 하지 않는다. */}
              {filtered
                ? `걸러진 ${shown.length}건 / 전체 ${all.total}건`
                : `${shown.length}건 중 ${all.total}건`}
            </span>
          </header>

          <form className="sfield" onSubmit={(event) => event.preventDefault()}>
            <label htmlFor="status">상태</label>
            <select id="status" value={status} onChange={(event) => change("status", event.target.value)}>
              <option value="">전체</option>
              {STATUS_ORDER.map((value) => (
                <option key={value} value={value}>
                  {STATUS_LABELS[value]}
                </option>
              ))}
            </select>

            <label htmlFor="assignee">담당</label>
            <select
              id="assignee"
              value={assignee}
              onChange={(event) => change("assignee", event.target.value)}
            >
              <option value="">전체</option>
              {/* 목록에 실제로 있는 담당자만 고를 수 있다. 없는 사람을 고르게 두지 않는다. */}
              {assignees.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>

            {filtered && (
              <button type="button" className="button button--quiet" onClick={() => setParams({})}>
                거르기 지우기
              </button>
            )}
          </form>

          {shown.length === 0 ? (
            <section className="state">
              <h2>조건에 맞는 작업이 없습니다</h2>
              <p>거르기를 지우면 전체 {all.total}건을 볼 수 있습니다.</p>
              <button type="button" className="button" onClick={() => setParams({})}>
                거르기 지우기
              </button>
            </section>
          ) : (
            <TaskRows projectId={project.id} tasks={shown} />
          )}

          <p className="n">취소된 작업은 완료율 분모에서 빠집니다. 상태는 GitHub가 정본입니다.</p>
        </section>
      )}
    </AppShell>
  );
}
