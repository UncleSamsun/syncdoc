import {useEffect,useState} from "react";
import {Link,useParams,useSearchParams} from "react-router-dom";
import {apiGet,isApiError} from "../../shared/api/client";
import type {ProjectItem} from "../projects/types";
import {useDocumentList} from "../documents/useDocument";
import AppShell from "../shell/AppShell";
import type {SnapshotPage,Summary,ResultPage,Change,Row,Impact,Ref,DesignImpact} from "./comparisonTypes";
const LABELS:Record<Change,string>={all:"전체",added:"추가",removed:"제외",modified:"원문 변경",moved:"위치 이동",moved_modified:"이동·원문 변경",unchanged:"동일",unknown:"미확인"};
const REASONS:Record<string,string>={identity_changed:"문서 ID 변경",confirmed_definition_set:"확정 정의 집합 변경",incomplete_index:"불완전한 항목 자료",duplicate_item_id:"중복 항목 ID",invalid_or_duplicate_document_id:"문서 ID 미확인",item_document_missing:"항목 문서 미확인",fingerprint_unavailable:"원문 해시 비교 불가",left_analysis_scope:"확정 분석 대상에서 제외",entered_analysis_scope:"확정 분석 대상으로 추가"};
const EXECUTION:Record<string,string>={done:"완료",in_progress:"진행 중",unregistered:"Issue 미등록",canceled:"취소",mapping_conflict:"Issue 연결 충돌"};
const STATUS={complete:"전 범위 비교",partial:"부분 비교",unchecked:"비교 미확인"};
const COVERAGE:Record<string,string>={complete:"확인된 관계 범위",incomplete:"관계 부분 확인",unknown:"관계 미확인",not_applicable:"확정 요구 없음"};
type Load<T>={state:"ready";data:T}|{state:"loading"|"idle"}|{state:"error";status:number;code:string};
function useLoad<T>(url:string|null):Load<T>{
 const [value,setValue]=useState<{url:string;result:Load<T>}>();
 useEffect(()=>{if(!url)return;let active=true;const controller=new AbortController();
  apiGet<T>(url,controller.signal).then(data=>{if(active)setValue({url,result:{state:"ready",data}});}).catch(error=>{if(active)setValue({url,result:{state:"error",status:isApiError(error)?error.status:0,code:isApiError(error)?error.code:"NETWORK"}});});
  return ()=>{active=false;controller.abort();};
 },[url]);
 return !url?{state:"idle"}:value?.url===url?value.result:{state:"loading"};
}
export default function ComparisonPage(){const {projectId=""}=useParams();return <Comparison key={projectId} projectId={projectId}/>;}
function Comparison({projectId}:{projectId:string}){
 const [params,setParams]=useSearchParams();const from=params.get("fromSnapshotId")??"",to=params.get("toSnapshotId")??"";
 const [snapshotPage,setSnapshotPage]=useState(0),[tab,setTab]=useState<"documents"|"items"|"impacts"|"design-impacts">("documents"),[change,setChange]=useState<Change>("all"),[kind,setKind]=useState("all");
 const pairIdentity=JSON.stringify([from,to]);
 const [pagination,setPagination]=useState({pair:pairIdentity,value:0});
 const page=pagination.pair===pairIdentity?pagination.value:0;
 const setPage=(value:number)=>setPagination({pair:pairIdentity,value});
 const project=useLoad<ProjectItem>(`/projects/${projectId}`);
 const snapshots=useLoad<SnapshotPage>(`/projects/${projectId}/snapshots?page=${snapshotPage}&size=20`);
 const {list}=useDocumentList(projectId);
 const pair=from&&to?`fromSnapshotId=${encodeURIComponent(from)}&toSnapshotId=${encodeURIComponent(to)}`:null;
 const base=`/projects/${projectId}/snapshot-comparison`;
 const summary=useLoad<Summary>(pair?`${base}?${pair}`:null);
 const rows=useLoad<ResultPage>(pair?`${base}/${tab}?${pair}&page=${page}&size=50&change=${change}${tab==="items"?`&kind=${kind}`:""}`:null);
 useEffect(()=>{
  if(from||to||snapshotPage!==0||snapshots.state!=="ready"||snapshots.data.items.length===0)return;
  const target=snapshots.data.items[0];
  const previous=snapshots.data.items.slice(1).find(s=>s.branch!==null&&s.docsRoot!==null&&s.branch===target.branch&&s.docsRoot===target.docsRoot&&s.comparisonReadiness!=="legacy");
  if(previous)setParams({fromSnapshotId:previous.snapshotId,toSnapshotId:target.snapshotId},{replace:true});
 },[from,to,snapshots,snapshotPage,setParams]);
 if(project.state==="error")return <main className="centered"><Failure error={project}/></main>;
 if(project.state!=="ready")return <main className="centered">프로젝트를 불러오는 중입니다.</main>;
 const options=snapshots.state==="ready"?[...snapshots.data.items]:[];
 for(const id of [from,to])if(id&&!options.some(s=>s.snapshotId===id)){
  const known=summary.state==="ready"?[summary.data.from,summary.data.to].find(s=>s.snapshotId===id):undefined;
  options.push(known??{snapshotId:id,sourceRevision:id,createdAt:"",branch:null,docsRoot:null,current:false,rendererVersion:"",policyVersion:"",comparisonReadiness:"legacy"});
 }
 const setSelection=(field:string,id:string)=>{const next=new URLSearchParams(params);if(id)next.set(field,id);else next.delete(field);setPage(0);setParams(next);};
 const validSummary=summary.state==="ready"&&summary.data.from.snapshotId===from&&summary.data.to.snapshotId===to?summary.data:null;
 const validRows=rows.state==="ready"&&rows.data.from.snapshotId===from&&rows.data.to.snapshotId===to?rows.data:null;
 return <AppShell project={project.data} documents={list.state==="ready"?list.list.items:[]} documentSnapshotId={list.state==="ready"?list.list.snapshotId:null} active="checklist">
  <section className="comparison-page">
   <header><h1>게시본 비교</h1><Link to={`/projects/${projectId}/checklist`}>산출물로 돌아가기</Link></header>
   <p className="n">원문 변화와 재검토 후보입니다. 요구 충족·구현 오류·승인 여부를 판정하지 않습니다.</p>
   {snapshots.state==="error"&&<Failure error={snapshots}/>}
   <div className="comparison-selectors">
    {([ ["fromSnapshotId","기준 게시본",from],["toSnapshotId","대상 게시본",to] ] as const).map(([field,label,value])=><label key={field}>{label}<select aria-label={label} value={value} onChange={e=>setSelection(field,e.target.value)}>
     <option value="">게시본 선택</option>{options.map(s=><option key={s.snapshotId} value={s.snapshotId}>{s.sourceRevision.slice(0,10)} · {s.branch??"범위 미기록"} / {s.docsRoot??"미기록"}{s.createdAt?` · ${new Date(s.createdAt).toLocaleString()}`:""}{s.comparisonReadiness==="legacy"?" · 비교 자료 없음":""}{s.current?" · 현재":""}</option>)}
    </select></label>)}
   </div>
   {snapshots.state==="ready"&&<Pager label="게시본 목록" page={snapshotPage} total={snapshots.data.totalElements} size={20} onPage={setSnapshotPage}/>}
   {!pair&&<p role="status">비교할 완료 게시본 두 개를 선택하세요. 새 비교 자료가 없다면 같은 범위의 재수집 자료가 필요합니다.</p>}
   {summary.state==="loading"&&<p role="status">비교 요약을 불러오는 중입니다.</p>}
   {summary.state==="error"&&<Failure error={summary}/>}
   {summary.state==="ready"&&!validSummary&&<p role="alert">조회 기준이 달라 결과 표시를 중지했습니다.</p>}
   {validSummary&&<>
    <h2>{STATUS[validSummary.status]}</h2>
    <p className="mono">{validSummary.from.sourceRevision.slice(0,10)} → {validSummary.to.sourceRevision.slice(0,10)}</p>
    <p className="n">생성 시각은 Git commit의 선후 관계를 보증하지 않습니다. {COVERAGE[validSummary.coverage]??"관계 미확인"}.</p>
    {validSummary.status==="unchecked"?<p role="status">이 게시본 쌍에는 지원하는 비교 자료 또는 수집 범위 정보가 없습니다. 다음 수집 자료를 확인하세요. 변경 없음으로 판정하지 않습니다.</p>:<>
     {validSummary.status==="partial"&&<p role="status">불완전한 항목의 미존재·변화는 미확인으로 남깁니다.</p>}
     <p className="n">{Object.entries(validSummary.counts??{}).map(([category,counts])=>`${category==="documents"?"문서":"항목"}: ${Object.entries(counts).filter(([,n])=>n>0).map(([c,n])=>`${LABELS[c as Change]??c} ${n}`).join(" · ")||"0"}`).join(" / ")}</p>
     <nav className="comparison-tabs" aria-label="비교 결과 종류">{([ ["documents","문서"],["items","REQ·TASK"],["impacts","재검토 후보"],["design-impacts","설계 재검토"] ] as const).map(([value,label])=><button type="button" key={value} aria-pressed={tab===value} onClick={()=>{setTab(value);setPage(0);setChange("all");}}>{label}</button>)}</nav>
     {tab!=="impacts"&&tab!=="design-impacts"&&<label>변경 종류 <select aria-label="변경 종류" value={change} onChange={e=>{setChange(e.target.value as Change);setPage(0);}}>{Object.entries(LABELS).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label>}
     {tab==="items"&&<label> 항목 종류 <select aria-label="항목 종류" value={kind} onChange={e=>{setKind(e.target.value);setPage(0);}}><option value="all">전체</option><option value="req">REQ</option><option value="task">TASK</option></select></label>}
     {rows.state==="loading"&&<p role="status">비교 행을 불러오는 중입니다.</p>}{rows.state==="error"&&<Failure error={rows}/>}
     {validRows&&validRows.status==="unchecked"&&<p role="status">설계 관계 자료가 없어 비교 미확인입니다. 새 수집 자료를 확인하세요.</p>}
     {validRows&&validRows.status==="partial"&&tab==="design-impacts"&&<p role="status">설계 관계는 부분 분석입니다. 후보가 없다는 사실은 영향 없음의 보증이 아닙니다.</p>}
     {validRows&&validRows.status!=="unchecked"&&<>
      <div className="tblwrap" tabIndex={0} role="region" aria-label="게시본 비교 표"><table className="mdtbl"><thead><tr>{tab==="impacts"?<><th>변경 요구</th><th>재검토 작업</th><th>현재 관찰 정보</th></>:tab==="design-impacts"?<><th>변경 요구·설계</th><th>기준 원문</th><th>대상 원문</th></>:<><th>변경</th><th>기준 원문</th><th>대상 원문</th></>}</tr></thead><tbody>
       {validRows.items.map((row,index)=>tab==="impacts"?<ImpactRow key={index} row={row as Impact} projectId={projectId} fullName={project.data.fullName} from={from} to={to}/>:tab==="design-impacts"?<tr key={`${(row as DesignImpact).requirementId}:${(row as DesignImpact).kind}:${(row as DesignImpact).itemId}`}><td>{(row as DesignImpact).requirementId} · {(row as DesignImpact).itemId}<span className="n">{(row as DesignImpact).presence==="unknown"?"대상 설계 미확인":(row as DesignImpact).presence==="removed"?"확정 정의에서 제외":"설계 재검토 후보"}</span></td><td><SourceLink projectId={projectId} snapshotId={from} source={(row as DesignImpact).before}/></td><td><SourceLink projectId={projectId} snapshotId={to} source={(row as DesignImpact).after}/></td></tr>:<tr key={(row as Row).key}><td>{LABELS[(row as Row).change]}{(row as Row).reason&&<span className="n"> · {REASONS[(row as Row).reason??""]??"항목 식별 정보 확인 필요"}</span>}</td><td><SourceLink projectId={projectId} snapshotId={from} source={(row as Row).before}/></td><td><SourceLink projectId={projectId} snapshotId={to} source={(row as Row).after}/></td></tr>)}
      </tbody></table></div>
      {validRows.items.length===0&&<p>현재 조건에 표시할 결과가 없습니다.{tab==="impacts"?" 후보가 없다는 사실은 영향 없음의 보증이 아닙니다.":""}</p>}
      <Pager label="비교 결과" page={page} size={50} total={validRows.totalElements} onPage={setPage}/>
     </>}
     {(tab==="design-impacts"&&validRows?validRows.findings:validSummary.findings).length>0&&<section><h3>비교 진단</h3><ul>{(tab==="design-impacts"&&validRows?validRows.findings:validSummary.findings).map((f,i)=><li key={i}>{f}</li>)}</ul></section>}
    </>}
   </>}
  </section>
 </AppShell>;
}
function SourceLink({projectId,snapshotId,source}:{projectId:string;snapshotId:string;source:Ref|null}){return source?<Link to={`/projects/${projectId}/documents/${source.documentId}?snapshotId=${encodeURIComponent(snapshotId)}${source.anchor?`#${encodeURIComponent(source.anchor)}`:""}`}><span className="mono">{source.itemId??source.specId??source.path}</span> {source.title}<span className="n"> · {source.path}{source.status?` · ${source.status}`:""}</span></Link>:<span>—</span>;}
function ImpactRow({row,projectId,fullName,from,to}:{row:Impact;projectId:string;fullName:string;from:string;to:string}){
 return <tr><td><span>{row.requirementId} · {LABELS[row.requirementChange as Change]??row.requirementChange}</span><div><SourceLink projectId={projectId} snapshotId={from} source={row.beforeRequirement}/></div><div><SourceLink projectId={projectId} snapshotId={to} source={row.afterRequirement}/></div></td><td><span>{row.taskId} · {row.taskPresence==="removed"?"확정 정의에서 제외":row.taskPresence==="unknown"?"대상 작업 미확인":"대상 작업"}</span><div><SourceLink projectId={projectId} snapshotId={from} source={row.beforeTask}/></div><div><SourceLink projectId={projectId} snapshotId={to} source={row.afterTask}/></div></td><td>{row.execution?<><span>{EXECUTION[row.execution.status]??"진행 상태 미확인"}</span>{row.execution.issueNumber&&<a href={`https://github.com/${fullName}/issues/${row.execution.issueNumber}`} target="_blank" rel="noopener noreferrer"> #{row.execution.issueNumber}</a>}{row.execution.pullRequests.map(n=><a key={n} href={`https://github.com/${fullName}/pull/${n}`} target="_blank" rel="noopener noreferrer"> PR#{n}</a>)}<span className="n"> · 관찰 {row.execution.observedAt?new Date(row.execution.observedAt).toLocaleString():"정보 없음"}</span></>:"현재 실행 상태 미확인"}</td></tr>;
}
function Failure({error}:{error:{status:number;code:string}}){const text=error.status===409?"같은 수집 브랜치와 문서 루트의 게시본을 선택하세요.":error.status===410?"선택한 게시본이 회수되었거나 제공되지 않습니다.":error.status===404?"프로젝트가 없거나 열람 권한이 없습니다.":error.status===400?"게시본 ID와 비교 조건을 확인하세요.":"비교 자료를 불러오지 못했습니다. 접근 상태와 연결을 확인하세요.";return <p role="alert">{text}</p>;}
function Pager({label,page,size,total,onPage}:{label:string;page:number;size:number;total:number;onPage:(page:number)=>void}){return <nav className="trace-pages" aria-label={`${label} 페이지`}><button disabled={page===0} type="button" onClick={()=>onPage(page-1)}>{label} 이전</button><span>{page+1} / {Math.max(1,Math.ceil(total/size))}</span><button disabled={(page+1)*size>=total} type="button" onClick={()=>onPage(page+1)}>{label} 다음</button></nav>;}
