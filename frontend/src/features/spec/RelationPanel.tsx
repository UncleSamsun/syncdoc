import {useEffect,useState} from 'react';
import {Link} from 'react-router-dom';
import {apiGet,isApiError} from '../../shared/api/client';
export type RelationItem={kind:string;itemId:string;title:string;documentId:string;path:string;anchor:string|null;line:number};
type Execution={status:string;issueNumber:number|null;pullRequests:number[];observedAt:string|null};
type View={snapshotId:string;sourceRevision:string;analysisStatus:'complete'|'partial'|'unchecked';uncheckedReason:string|null;requirements:{requirement:RelationItem;designCoverage:string;designs:RelationItem[];tasks:{item:RelationItem;execution:Execution|null}[]}[];findings:{code:string;documentId:string|null;path:string;line:number;message:string;targetId:string|null}[];totalElements:number;page:number;size:number};
const STATES={complete:'전 범위 분석',partial:'부분 분석',unchecked:'미분석'};
const EXECUTION:Record<string,string>={done:'완료',in_progress:'진행 중',in_review:'검토 중',not_started:'시작 전',unregistered:'Issue 미등록',canceled:'취소',mapping_conflict:'연결 충돌'};
export default function RelationPanel(props:{projectId:string;snapshotId:string;fullName:string}){return <Panel key={JSON.stringify([props.projectId,props.snapshotId])} {...props}/>;}
function Panel({projectId,snapshotId,fullName}:{projectId:string;snapshotId:string;fullName:string}){
 const [page,setPage]=useState(0),[state,setState]=useState<{url:string;data?:View;error?:number}>();
 const url=`/projects/${projectId}/spec-relations?snapshotId=${encodeURIComponent(snapshotId)}&page=${page}&size=50`;
 useEffect(()=>{let active=true;const controller=new AbortController();apiGet<View>(url,controller.signal).then(data=>{if(active)setState({url,data});}).catch(e=>{if(active)setState({url,error:isApiError(e)?e.status:0});});return()=>{active=false;controller.abort();};},[url]);
 const current=state?.url===url?state:undefined,view=current?.data;
 return <section className='traceability panel' aria-labelledby='relations-title'><h2 id='relations-title'>요구·설계·작업</h2><p className='n'>관계는 게시본 기준입니다. 연결은 요구 충족 증명이 아니며 UI/API 적용 여부는 별도입니다. 실행 상태는 현재 GitHub 관찰입니다.</p>
  {!current?<p role='status'>설계 관계를 불러오는 중입니다.</p>:current.error!==undefined?<p role='alert'>{current.error===410?'이 게시본은 제공되지 않습니다.':current.error===404?'프로젝트가 없거나 권한이 없습니다.':'설계 관계를 불러오지 못했습니다.'}</p>:view&&view.snapshotId!==snapshotId?<p role='alert'>조회 기준이 달라 표시를 중지했습니다.</p>:view&&<>
   <p><span className='chip'>{STATES[view.analysisStatus]}</span> <span className='mono'>{view.sourceRevision}</span></p>
   {view.analysisStatus==='unchecked'?<p role='status'>이 게시본의 관계 자료가 없습니다. 새 수집 자료를 확인하세요.</p>:<><div className='tblwrap' role='region' tabIndex={0} aria-label='요구·설계·작업 표'><table className='mdtbl'><thead><tr><th>요구</th><th>UI·API</th><th>작업·현재 관찰</th></tr></thead><tbody>{view.requirements.map(row=><tr key={row.requirement.documentId+':'+row.requirement.itemId+':'+row.requirement.line}><td><Source {...{projectId,snapshotId}} item={row.requirement}/></td><td>{row.designs.length?<ul>{row.designs.map(item=><li key={item.kind+item.itemId}><Source {...{projectId,snapshotId,item}}/></li>)}</ul>:row.designCoverage==='unknown'?'설계 관계 미확인':'연결 정의 없음 · 적용 여부 별도'}</td><td><ul>{row.tasks.map(task=><li key={task.item.itemId}><Source {...{projectId,snapshotId}} item={task.item}/>{task.execution?<><span> · {EXECUTION[task.execution.status]??'상태 미확인'}</span>{task.execution.issueNumber&&<a href={`https://github.com/${fullName}/issues/${task.execution.issueNumber}`} target='_blank' rel='noopener noreferrer'> #{task.execution.issueNumber}</a>}{task.execution.pullRequests.map(n=><a key={n} href={`https://github.com/${fullName}/pull/${n}`} target='_blank' rel='noopener noreferrer'> PR#{n}</a>)}<span className='n'> · 관찰 {task.execution.observedAt?new Date(task.execution.observedAt).toLocaleString():'정보 없음'}</span></>:' · 실행 상태 미확인'}</li>)}</ul></td></tr>)}</tbody></table></div>
   {!view.requirements.length&&<p>표시할 확정 요구가 없습니다. 충족이나 적용 제외 판정은 아닙니다.</p>}
   <nav className='trace-pages' aria-label='설계 관계 페이지'><button disabled={page===0} onClick={()=>setPage(page-1)}>설계 관계 이전</button><span>{page+1} / {Math.max(1,Math.ceil(view.totalElements/view.size))}</span><button disabled={(page+1)*view.size>=view.totalElements} onClick={()=>setPage(page+1)}>설계 관계 다음</button></nav></>}
   <h3>설계 참조 진단</h3><ul>{view.findings.map((f,i)=><li key={i}><span className='mono'>{f.code}</span> {f.documentId?<Link to={`/projects/${projectId}/documents/${f.documentId}?snapshotId=${encodeURIComponent(snapshotId)}`}>{f.path}:{f.line}</Link>:<span>{f.path}:{f.line}</span>} {f.message} {f.targetId}</li>)}</ul>
  </>}
 </section>;
}
function Source({projectId,snapshotId,item}:{projectId:string;snapshotId:string;item:RelationItem}){return <Link to={`/projects/${projectId}/documents/${item.documentId}?snapshotId=${encodeURIComponent(snapshotId)}${item.anchor?'#'+encodeURIComponent(item.anchor):''}`}><span className='mono'>{item.itemId}</span> {item.title}</Link>;}
