import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { apiGet, apiPost, isApiError } from "../../shared/api/client";
import { AccessUnavailable, DocumentMissing } from "../documents/DocumentStates";
import { useDocumentList } from "../documents/useDocument";
import type { ProjectItem, SyncStatus } from "../projects/types";
import AppShell from "../shell/AppShell";
import { errorCountOf } from "../spec/types";
import { useChecklist } from "../spec/useChecklist";
import { FirstSyncWaiting, StaleBanner } from "../sync/SyncStates";
import { formatMoment } from "../sync/syncLabels";
import TaskTable from "./TaskTable";
import type { OverviewView } from "./types";
import { isPartial } from "./types";

type Props = { csrfToken?: string };

/**
 * UI-002 현황.
 *
 * <p>범위를 밝히지 않은 숫자를 두지 않는다. 완료율에는 분모와 기준을, 조회하지 못한 값에는
 * 그 사실을 함께 둔다. 0%와 `계산 대상 없음`과 `조회 불가`는 서로 다른 화면 요소다.
 */
export default function OverviewPage({ csrfToken }: Props) {
  const { projectId } = useParams();
  const [project, setProject] = useState<ProjectItem | null>(null);
  const [status, setStatus] = useState<SyncStatus | undefined>();
  const [overview, setOverview] = useState<OverviewView | null>(null);
  const [failure, setFailure] = useState<"missing" | "unavailable" | undefined>();
  const [syncing, setSyncing] = useState(false);
  const { list } = useDocumentList(projectId);
  // 사이드바 건수는 어느 화면에서도 같아야 한다. 한 화면에만 두면 옮길 때마다 값이 사라진다.
  const checklist = useChecklist(projectId);

  const load = () => {
    if (!projectId) {
      return;
    }
    apiGet<ProjectItem>(`/projects/${projectId}`)
      .then(setProject)
      .catch((error) =>
        setFailure(isApiError(error) && error.status === 404 ? "missing" : "unavailable"),
      );
    apiGet<OverviewView>(`/projects/${projectId}/overview`).then(setOverview).catch((error) => {
      // 볼 수 없는 프로젝트는 없는 프로젝트와 같은 404다. 여기서 둘을 가르면 화면이
      // "권한을 확인할 수 없다"고 말하게 되고, 그건 없는 것과 구분되는 정보가 된다.
      setFailure(isApiError(error) && error.status === 404 ? "missing" : "unavailable");
    });
    apiGet<SyncStatus>(`/projects/${projectId}/sync`).then(setStatus).catch(() => {
      // 수집 상태를 못 읽는다고 현황까지 막지 않는다.
    });
  };

  useEffect(load, [projectId]);

  const requestSync = async () => {
    if (!projectId || !csrfToken || syncing) {
      return;
    }
    setSyncing(true);
    try {
      await apiPost(`/projects/${projectId}/sync`, {}, csrfToken);
      load();
    } finally {
      setSyncing(false);
    }
  };

  if (failure === "missing") {
    return <DocumentMissing projectId={projectId ?? ""} />;
  }
  if (failure === "unavailable") {
    return <AccessUnavailable />;
  }
  if (!project || !overview) {
    return <main className="centered">불러오는 중입니다.</main>;
  }

  const documents = list.state === "ready" ? list.list.items : [];
  const partial = isPartial(overview.partial);

  return (
    <AppShell
      project={project}
      status={status}
      documents={documents}
      taskCount={overview.taskTotal}
      checklistErrors={checklist.state === "ready" ? errorCountOf(checklist.checklist) : undefined}
      active="overview"
    >
      {status && <StaleBanner status={status} />}
      {overview.snapshotId === null ? (
        <FirstSyncWaiting />
      ) : (
        <section className="ov">
          <header className="ov-h">
            <h1>현황</h1>
            <span className="mono">
              {overview.snapshotId.slice(0, 8)} · {overview.sourceRevision?.slice(0, 10)}
            </span>
            {csrfToken && (
              <button type="button" className="button" onClick={requestSync} disabled={syncing}>
                지금 동기화
              </button>
            )}
          </header>

          <div className="cards">
            <SyncCard status={status} />
            <article className="card">
              <div className="k">작업 완료</div>
              <div className="v">
                {/* 분모 없이 숫자만 두지 않는다. 분모가 0이면 0%가 아니라 계산 대상 없음이다. */}
                {overview.progress.denominator === 0
                  ? "계산 대상 없음"
                  : `${overview.progress.completed} / ${overview.progress.denominator}`}
                {partial && <span className="chip chip--rev">확정 아님</span>}
              </div>
              <div className="n">
                {`상태 명시 ${overview.counts.total}건 기준 · 취소 ${overview.counts.canceled}건 제외`}
              </div>
            </article>
            <ProjectCard access={overview.projectAccess} />
            <article className="card">
              <div className="k">문서</div>
              <div className="v">{overview.documentCount}</div>
              <div className="n">
                마지막 정상 게시본 기준 · {formatMoment(overview.repositoryObservedAt) ?? "—"}
              </div>
            </article>
          </div>

          <StatusBar overview={overview} partial={partial} />

          <TaskTable
            projectId={project.id}
            tasks={overview.tasks}
            total={overview.taskTotal}
            unmatched={overview.unmatchedIssueTasks}
          />

          <section className="panel">
            <h2>최근 변경</h2>
            {overview.recentChanges.length === 0 ? (
              <p className="n">이전 게시본과 견줘 달라진 문서가 없습니다.</p>
            ) : (
              <ul className="changes">
                {overview.recentChanges.map((change) => (
                  <li key={change.documentId}>
                    <span className={`st st--${change.change === "added" ? "ok" : "review"}`}>
                      <span className="dot" aria-hidden="true" />
                      {change.change === "added" ? "추가" : "수정"}
                    </span>
                    <Link to={`/projects/${project.id}/documents/${change.documentId}`}>
                      {change.title}
                    </Link>
                    <span className="mono">{change.path}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </section>
      )}
    </AppShell>
  );
}

/** 동기화 카드. 실패 중에는 마지막 성공 시각과 오류 코드를 함께 둔다. */
function SyncCard({ status }: { status?: SyncStatus }) {
  const failed = status?.state === "failed";
  return (
    <article className={`card${failed ? " card--fail" : ""}`}>
      <div className="k">동기화</div>
      <div className="v">{failed ? "오래된 정보" : (formatMoment(status?.lastSuccessAt) ?? "—")}</div>
      <div className="n">
        {failed ? (
          <>
            마지막 성공 {formatMoment(status?.lastSuccessAt) ?? "없음"} · 실패{" "}
            {formatMoment(status?.lastAttemptAt) ?? "—"}
            {status?.errorCode && <span className="mono"> {status.errorCode}</span>}
          </>
        ) : (
          "마지막 성공 시각"
        )}
      </div>
    </article>
  );
}

/**
 * UI-009. Project 완료율 자리.
 *
 * <p>0%나 빈 막대를 그리지 않는다. 연결하지 않은 것과 볼 수 없는 것은 할 일이 다르므로 문구도 다르다.
 */
function ProjectCard({ access }: { access: OverviewView["projectAccess"] }) {
  if (access === "available") {
    return (
      <article className="card">
        <div className="k">Project 완료율</div>
        <div className="v">조회함</div>
        <div className="n">Project 상태 기준</div>
      </article>
    );
  }
  const notConnected = access === "not_connected";
  return (
    <article className="card card--dashed">
      <div className="k">Project 완료율</div>
      <div className="v">{notConnected ? "연결 안 함" : "조회 불가"}</div>
      <div className="n">
        <span className="mono">progress: null</span>
        <br />
        {notConnected
          ? "이 프로젝트에 GitHub Project를 연결하지 않았습니다. 연결하면 Project 기반 완료율을 함께 보여줍니다."
          : "이 계정으로 연결된 GitHub Project를 조회할 수 없습니다. Project 기반 완료율은 표시하지 않습니다. 문서와 Issue 현황은 그대로 사용할 수 있습니다."}
      </div>
    </article>
  );
}

/** 상태별 건수. 분모가 0이면 막대를 그리지 않는다. UI-010은 숫자를 숨기지 않고 칩만 붙인다. */
function StatusBar({ overview, partial }: { overview: OverviewView; partial: boolean }) {
  const { counts } = overview;
  const segments = [
    { key: "inProgress", label: "진행", value: counts.inProgress, tone: "prog" },
    { key: "done", label: "완료", value: counts.done, tone: "done" },
    { key: "unregistered", label: "Issue 미등록", value: counts.unregistered, tone: "todo" },
    { key: "canceled", label: "취소", value: counts.canceled, tone: "off" },
  ];
  const denominator = counts.total - counts.canceled;

  return (
    <section className="panel">
      <h2>
        상태별 건수
        {partial && <span className="chip chip--rev">수집 중 · 확정 아님</span>}
      </h2>
      {counts.total === 0 ? (
        <p className="n">계산 대상 없음</p>
      ) : (
        <>
          <div className="bar" role="img" aria-label="작업 상태 분포">
            {segments
              .filter((segment) => segment.value > 0)
              .map((segment) => (
                <span
                  key={segment.key}
                  className={`seg seg--${segment.tone}`}
                  style={{ flexGrow: segment.value }}
                />
              ))}
          </div>
          <ul className="legend">
            {segments.map((segment) => (
              <li key={segment.key}>
                <span className={`swatch swatch--${segment.tone}`} aria-hidden="true" />
                {segment.label} {segment.value}
                {segment.key === "canceled" && segment.value > 0 && (
                  <span className="n"> 분모 제외</span>
                )}
              </li>
            ))}
          </ul>
          {partial && (
            <p className="n">전체 페이지 수집이 끝나지 않았습니다. 지금 값은 수집된 범위의 중간 집계입니다.</p>
          )}
          {denominator === 0 && <p className="n">계산 대상 없음</p>}
        </>
      )}
    </section>
  );
}
