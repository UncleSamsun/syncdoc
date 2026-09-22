import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { apiGet, isApiError } from "../../shared/api/client";
import { AccessUnavailable, DocumentMissing, SnapshotGone } from "../documents/DocumentStates";
import { useDocumentList } from "../documents/useDocument";
import type { ProjectItem } from "../projects/types";
import AppShell from "../shell/AppShell";
import { FirstSyncWaiting } from "../sync/SyncStates";
import type { ChecklistFinding, ChecklistStatus, ChecklistType, ChecklistView,
  UncheckedReason } from "./types";
import { errorCountOf } from "./types";
import { useChecklist } from "./useChecklist";

/**
 * UI-013 산출물 체크리스트.
 *
 * <p>미검사를 통과로 바꾸지 않는다. 오류가 0건이라는 이유로도 바꾸지 않는다. 읽지 못한 것과
 * 지킨 것은 다르다. 미작성과 오류도 같은 문구로 두지 않는다.
 *
 * <p>기준을 고치는 조작을 두지 않는다. 정의 파일과 적용 Spec 표는 저장소에서 고친다. REQ-008이
 * 정한 경계이며, 서비스가 기준의 정본이 되면 "명세가 정본"이라는 전제가 깨진다.
 */
export default function ChecklistPage() {
  const { projectId } = useParams();
  const [project, setProject] = useState<ProjectItem | null>(null);
  const [failure, setFailure] = useState<"missing" | "unavailable" | undefined>();
  const { list } = useDocumentList(projectId);
  const checklist = useChecklist(projectId);

  useEffect(() => {
    if (!projectId) {
      return;
    }
    apiGet<ProjectItem>(`/projects/${projectId}`)
      .then(setProject)
      .catch((error) =>
        setFailure(isApiError(error) && error.status === 404 ? "missing" : "unavailable"),
      );
  }, [projectId]);

  if (failure === "missing" || checklist.state === "missing") {
    return <DocumentMissing projectId={projectId ?? ""} />;
  }
  if (failure === "unavailable" || checklist.state === "unavailable") {
    return <AccessUnavailable />;
  }
  if (!project) {
    return <main className="centered">불러오는 중입니다.</main>;
  }

  const documents = list.state === "ready" ? list.list.items : [];

  return (
    <AppShell project={project} documents={documents} active="checklist"
      checklistErrors={checklist.state === "ready" ? errorCountOf(checklist.checklist) : undefined}>
      {checklist.state === "loading" && <p className="centered">불러오는 중입니다.</p>}
      {checklist.state === "waiting" && <FirstSyncWaiting />}
      {checklist.state === "gone" && <SnapshotGone projectId={project.id} />}
      {checklist.state === "ready" && <Body projectId={project.id} view={checklist.checklist} />}
    </AppShell>
  );
}

function Body({ projectId, view }: { projectId: string; view: ChecklistView }) {
  const applied = view.types.filter((type) => type.apply === "적용").length;
  const documents = view.types.reduce((total, type) => total + type.documents.length, 0);
  const pending = view.types.filter((type) => type.status === "pending").length;

  return (
    <section className="chk">
      <header className="chk-h">
        <h1>산출물 체크리스트</h1>
        <span className="mono">
          {view.snapshotId.slice(0, 8)} · {view.sourceRevision.slice(0, 10)}
        </span>
        <StatusChip status={view.status} count={errorCountOf(view)} />
      </header>

      {view.status === "unchecked" ? (
        <Unchecked reason={view.uncheckedReason} />
      ) : (
        <>
          <p className="n">
            {`적용 ${applied}종 · 문서 ${documents}건 · 오류 ${errorCountOf(view)}건 · 미작성 ${pending}종`}
          </p>

          {view.findings.length > 0 && (
            <section className="panel">
              <h2>종류에 붙지 않는 오류</h2>
              <p className="n">문서를 분류할 수 없거나 적용 Spec 표 자체에 문제가 있습니다.</p>
              <FindingList projectId={projectId} findings={view.findings} view={view} />
            </section>
          )}

          <div className="tblwrap">
            <table className="mdtbl chk-t">
              <thead>
                <tr>
                  <th>종류</th>
                  <th>적용</th>
                  <th>상태</th>
                  <th>문서</th>
                  <th>오류</th>
                </tr>
              </thead>
              <tbody>
                {view.types.map((type) => (
                  <TypeRow key={type.type} projectId={projectId} type={type} view={view} />
                ))}
              </tbody>
            </table>
          </div>

          {view.truncated && (
            <p className="n" role="status">오류가 많아 일부만 보여줍니다 · 저장소에서 확인하세요</p>
          )}
        </>
      )}

      <p className="n">
        기준은 저장소에서 고칩니다. 이 화면은 읽기만 합니다.
      </p>
    </section>
  );
}

function TypeRow({ projectId, type, view }: { projectId: string; type: ChecklistType;
  view: ChecklistView }) {
  const [open, setOpen] = useState(false);
  const detailed = type.documents.length > 0 || type.findings.length > 0;

  return (
    <>
      <tr className={type.status === null ? "row--muted" : ""}>
        <td>
          {detailed ? (
            <button type="button" className="linkish" aria-expanded={open}
              onClick={() => setOpen((shown) => !shown)}>
              {type.name}
            </button>
          ) : (
            type.name
          )}
          <span className="mono"> {type.type}</span>
        </td>
        <td>
          {type.apply}
          {/* 보류·미적용은 왜 그런지 함께 보인다. 사유 없이 빠뜨린 것과 구분한다. */}
          {type.reason && <span className="n"> · {type.reason}</span>}
        </td>
        <td>{type.status === null ? "" : <StatusChip status={type.status}
          count={type.findings.length} />}</td>
        <td className="mono">{type.documents.length}</td>
        <td className="mono">{type.findings.length}</td>
      </tr>
      {open && (
        <tr>
          <td colSpan={5}>
            {type.documents.length > 0 && (
              <ul className="chk-docs">
                {type.documents.map((document) => (
                  <li key={document.documentId}>
                    <Link to={`/projects/${projectId}/documents/${document.documentId}`}>
                      {document.path}
                    </Link>
                    {document.specId && <span className="mono"> {document.specId}</span>}
                  </li>
                ))}
              </ul>
            )}
            <FindingList projectId={projectId} findings={type.findings} view={view} />
          </td>
        </tr>
      )}
    </>
  );
}

function FindingList({ projectId, findings, view }: { projectId: string;
  findings: ChecklistFinding[]; view: ChecklistView }) {
  if (findings.length === 0) {
    return null;
  }
  return (
    <ul className="chk-f">
      {findings.map((finding, index) => (
        <li key={`${finding.path}-${finding.line}-${index}`}>
          <span className="mono">{finding.check}</span>
          <Where projectId={projectId} finding={finding} view={view} />
          <span className="x">{finding.message}</span>
        </li>
      ))}
    </ul>
  );
}

/** 오류가 가리키는 자리. 문서가 있으면 그 문서로 간다. */
function Where({ projectId, finding, view }: { projectId: string; finding: ChecklistFinding;
  view: ChecklistView }) {
  if (!finding.path) {
    return <span className="mono">문서 없음</span>;
  }
  const documentId = finding.documentId ?? documentIdOf(view, finding.path);
  const where = finding.line === null ? finding.path : `${finding.path}:${finding.line}`;
  if (!documentId) {
    // 수집한 문서가 아니다. 적용 Spec 표처럼 문서 경로 밖의 파일이 여기 온다.
    return <span className="mono">{where}</span>;
  }
  return (
    <Link className="mono" to={`/projects/${projectId}/documents/${documentId}`}>
      {where}
    </Link>
  );
}

function documentIdOf(view: ChecklistView, path: string): string | null {
  for (const type of view.types) {
    const found = type.documents.find((document) => document.path === path);
    if (found) {
      return found.documentId;
    }
  }
  return null;
}

const STATUS_LABELS: Record<ChecklistStatus, string> = {
  pass: "통과",
  error: "오류",
  pending: "미작성",
  unchecked: "미검사",
};

const STATUS_TONES: Record<ChecklistStatus, string> = {
  pass: "ok",
  error: "fail",
  pending: "todo",
  unchecked: "review",
};

function StatusChip({ status, count }: { status: ChecklistStatus; count: number }) {
  return (
    <span className={`chip chip--${STATUS_TONES[status]}`}>
      {status === "error" ? `오류 ${count}건` : STATUS_LABELS[status]}
    </span>
  );
}

const UNCHECKED_TEXT: Record<UncheckedReason, [string, string]> = {
  DEFINITION_MISSING: [
    "규칙 정의 파일이 없어 검사하지 않았습니다",
    "저장소 루트의 rules/spec-format.json을 읽지 못했습니다.",
  ],
  APPLY_TABLE_MISSING: [
    "적용 Spec 표가 없어 검사하지 않았습니다",
    "저장소 루트의 rules/project-settings.md에서 적용 Spec 표를 읽지 못했습니다.",
  ],
  NO_DOCUMENTS: ["검사할 문서가 없어 검사하지 않았습니다", "이 게시본에 문서가 없습니다."],
  NOT_COMPUTED: ["이 게시본은 규약 판정 전에 만들어졌습니다", "다음 수집에서 다시 만들어집니다."],
};

/** 읽지 못한 것과 지킨 것은 다르다. 판정한 것처럼 쓰지 않는다. */
function Unchecked({ reason }: { reason: UncheckedReason | null }) {
  const [title, detail] = reason
    ? UNCHECKED_TEXT[reason]
    : ["검사하지 않았습니다", "규칙 파일을 읽지 못했습니다."];
  return (
    <section className="state">
      <h2>{title}</h2>
      <p>{detail}</p>
      <p className="n">검사하지 않은 것을 통과로 표시하지 않습니다.</p>
    </section>
  );
}
