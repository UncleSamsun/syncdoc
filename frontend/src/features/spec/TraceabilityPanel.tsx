import { useState } from "react";
import { Link } from "react-router-dom";
import { useTraceability } from "./useTraceability";
import type { Coverage, TraceItem } from "./traceabilityTypes";

const STATES = {complete:"전 범위 분석",partial:"부분 분석",unchecked:"미분석"};
const COVERAGE = {linked:"작업 연결",unlinked:"작업 미연결",unknown:"판정 보류"};
const REASONS: Record<string,string> = {NOT_COMPUTED:"분석 기능 도입 전 게시본입니다. 다음 수집에서 분석합니다.",
 NO_CONFIRMED_REQUIREMENTS:"확정 요구가 없어 분석하지 않았습니다.",NO_CONFIRMED_TASKS:"확정 작업이 없어 연결 판정을 보류합니다."};
const FAILURES = {loading:"관계를 불러오는 중입니다.",waiting:"첫 수집이 끝나면 관계를 확인할 수 있습니다.",
 missing:"프로젝트가 없거나 열람 권한이 없습니다.",gone:"이 게시본은 회수되어 더 이상 제공되지 않습니다.",unavailable:"관계를 불러오지 못했습니다. 접근 상태와 연결을 확인하세요."};
const EXECUTION: Record<string,string> = {not_started:"시작 전",in_progress:"진행 중",in_review:"검토 중",done:"완료",unregistered:"Issue 미등록",canceled:"취소",mapping_conflict:"Issue 연결 충돌"};
type Props = {projectId:string;snapshotId:string;fullName:string};
export default function TraceabilityPanel(props:Props) {
  // Switching project/snapshot resets page/filter state and cancels prior requests.
  return <Panel key={`${props.projectId}:${props.snapshotId}`} {...props}/>;
}
function Panel({projectId,snapshotId,fullName}:Props) {
  const [coverage,setCoverage] = useState<Coverage>("all"), [page,setPage] = useState(0), [findingPage,setFindingPage] = useState(0);
  const result = useTraceability(projectId,snapshotId,coverage,page,findingPage);
  return <section className="traceability panel" aria-labelledby="traceability-title">
    <h2 id="traceability-title">요구와 작업</h2>
    <p className="n">연결 여부는 요구 충족의 증명이 아닙니다. 관계는 게시본 기준이며 Issue·PR 상태는 GitHub 관찰 시점의 값입니다.</p>
    {result.state !== "ready" ? <p role="status">{FAILURES[result.state]}</p> : <>
      <p><span className="chip">{STATES[result.view.analysisStatus]}</span> <span className="mono">{result.view.sourceRevision.slice(0,10)}</span></p>
      {result.view.uncheckedReason && <p role="status">{REASONS[result.view.uncheckedReason] ?? "이 게시본은 분석하지 않았습니다."}</p>}
      {result.view.analysisStatus === "partial" && <p className="n">해석 불가 항목이 있어 확인되지 않은 연결은 판정을 보류합니다.</p>}
      <label>요구 연결 상태 <select aria-label="요구 연결 상태" value={coverage} onChange={e=>{setCoverage(e.target.value as Coverage);setPage(0);}}>
        <option value="all">전체</option>{Object.entries(COVERAGE).map(([value,label])=><option key={value} value={value}>{label}</option>)}
      </select></label>
      <div className="tblwrap" tabIndex={0} role="region" aria-label="요구–작업 연결 표"><table className="mdtbl"><thead><tr><th>요구</th><th>연결</th><th>작업·Issue</th></tr></thead>
        <tbody>{result.view.requirements.map(row=><tr key={`${row.item.documentId}:${row.item.itemId}:${row.item.line}`}>
          <td><ItemLink projectId={projectId} snapshotId={snapshotId} item={row.item}/></td>
          <td>{COVERAGE[row.coverage]}</td><td>{row.tasks.length===0 ? "—" : <ul>{row.tasks.map(task=><li key={task.item.itemId}>
            <ItemLink projectId={projectId} snapshotId={snapshotId} item={task.item}/>{" · "}
            {task.execution ? <>
              {task.execution.issueNumber ? <a href={`https://github.com/${fullName}/issues/${task.execution.issueNumber}`} target="_blank" rel="noopener noreferrer">#{task.execution.issueNumber}</a> : null}
              {" "}{EXECUTION[task.execution.status] ?? task.execution.status}
              {task.execution.pullRequests.map(number=><a key={number} href={`https://github.com/${fullName}/pull/${number}`} target="_blank" rel="noopener noreferrer"> PR #{number}</a>)}
              <span className="n"> · 관찰 {task.execution.observedAt ? new Date(task.execution.observedAt).toLocaleString() : "정보 없음"}</span>
            </> : "실행 상태 미확인"}
          </li>)}</ul>}</td>
        </tr>)}</tbody></table></div>
      {result.view.requirements.length===0 && <p className="n">현재 조건에 표시할 요구가 없습니다.</p>}
      <Pager label="요구" page={page} size={result.view.size} total={result.view.totalElements} onPage={setPage}/>
      <h3>참조 진단 <span className="mono">{result.findings.totalElements}</span></h3>
      <ul className="chk-f">{result.findings.findings.map((finding,index)=><li key={`${finding.path}:${finding.line}:${finding.code}:${index}`}>
        <span className="mono">{finding.code}</span>{" "}
        <Link to={`/projects/${projectId}/documents/${finding.documentId}?snapshotId=${encodeURIComponent(snapshotId)}`}>{finding.path}:{finding.line}</Link>{" "}
        <span>{finding.message}</span>{finding.targetId && <code>{finding.targetId}</code>}
      </li>)}</ul>
      {result.findings.totalElements===0 && <p className="n">이 분석 결과에 기록된 참조 진단이 없습니다.</p>}
      <Pager label="진단" page={findingPage} size={result.findings.size} total={result.findings.totalElements} onPage={setFindingPage}/>
    </>}
  </section>;
}
function ItemLink({projectId,snapshotId,item}:{projectId:string;snapshotId:string;item:TraceItem}) {
  return <Link to={`/projects/${projectId}/documents/${item.documentId}?snapshotId=${encodeURIComponent(snapshotId)}${item.anchor?`#${encodeURIComponent(item.anchor)}`:""}`}>
    <span className="mono">{item.itemId}</span> {item.title}</Link>;
}
function Pager({label,page,size,total,onPage}:{label:string;page:number;size:number;total:number;onPage:(page:number)=>void}) {
  return <nav className="trace-pages" aria-label={`${label} 페이지`}>
    <button type="button" disabled={page===0} onClick={()=>onPage(page-1)}>{label} 이전</button>
    <span>{page+1} / {Math.max(1,Math.ceil(total/size))}</span>
    <button type="button" disabled={(page+1)*size>=total} onClick={()=>onPage(page+1)}>{label} 다음</button>
  </nav>;
}
