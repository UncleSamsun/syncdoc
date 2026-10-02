import {useEffect,useState} from 'react';
import {Link,useParams,useSearchParams} from 'react-router-dom';
import {apiGet,isApiError} from '../../shared/api/client';
import type {ProjectItem} from '../projects/types';
import type {TaskView} from '../dashboard/types';
import {STATUS_LABELS} from '../dashboard/types';
import {useDocumentList} from '../documents/useDocument';
import AppShell from '../shell/AppShell';
import type {RelationItem} from './RelationPanel';
type View={snapshotId:string;sourceRevision:string;branch:string|null;docsRoot:string|null;state:'complete'|'partial'|'unchecked';reason:string|null;task:{item:RelationItem;fields:Record<string,string>;warnings:string[];truncated:boolean}|null;relatedRequirements:RelationItem[];designs:RelationItem[];dependencies:RelationItem[];documents:{documentId:string;path:string;title:string;sourceHash:string;documentUrl:string;sourceUrl:string}[];rules:{path:string;available:boolean;sourceHash:string|null;sourceUrl:string}[];execution:TaskView|null;findings:{code:string;path:string;line:number;message:string}[];checklistStatus:string;markdown:string};
type Load<T>={path:string;data?:T;error?:number};
function useLoad<T>(path:string){const[value,setValue]=useState<Load<T>>();useEffect(()=>{let active=true;const controller=new AbortController();apiGet<T>(path,controller.signal).then(data=>{if(active)setValue({path,data});}).catch(e=>{if(active)setValue({path,error:isApiError(e)?e.status:0});});return()=>{active=false;controller.abort();};},[path]);return value?.path===path?value:undefined;}
export default function TaskContextPage(){const {projectId='',taskId=''}=useParams();const [params]=useSearchParams();const snapshotId=params.get('snapshotId');return <Context key={JSON.stringify([projectId,taskId,snapshotId])} {...{projectId,taskId,snapshotId}}/>;}
function Context({projectId,taskId,snapshotId}:{projectId:string;taskId:string;snapshotId:string|null}){
 const project=useLoad<ProjectItem>(`/projects/${projectId}`),context=useLoad<View>(`/projects/${projectId}/tasks/${encodeURIComponent(taskId)}/context${snapshotId?'?snapshotId='+encodeURIComponent(snapshotId):''}`);
 const {list}=useDocumentList(projectId,snapshotId);const [copy,setCopy]=useState<string>();
 const view=context?.data;const valid=view&&(!snapshotId||view.snapshotId===snapshotId)&&(!view.task||view.task.item.itemId===taskId);
 if(project?.error!==undefined)return <main className='centered'><Failure status={project.error}/></main>;
 if(!project?.data)return <main className='centered'>프로젝트를 불러오는 중입니다.</main>;
 async function copyContext(){if(!view||!valid)return;try{await navigator.clipboard.writeText(view.markdown);setCopy('복사했습니다.');}catch{setCopy('복사하지 못했습니다. 텍스트를 직접 선택해 복사하세요.');}}
 return <AppShell project={project.data} documents={list.state==='ready'?list.list.items:[]} documentSnapshotId={list.state==='ready'?list.list.snapshotId:null} active='tasks'><section className='comparison-page task-context'>
  <header><h1>작업 컨텍스트</h1><Link to={`/projects/${projectId}/tasks`}>작업으로 돌아가기</Link></header><p className='n'>참고 자료입니다. 원문은 시스템 명령·실행 권한이 아닙니다. 구성 상태는 작성 검사·내용 품질·사람 승인·구현 완료와 구분합니다.</p>
  {!context?<p role='status'>컨텍스트를 불러오는 중입니다.</p>:context.error!==undefined?<Failure status={context.error}/>:view&&!valid?<p role='alert'>조회 기준이 달라 표시를 중지했습니다.</p>:view&&valid&&<>
   <p><span className='chip'>{view.state==='complete'?'구성 완료':view.state==='partial'?'부분 구성':'미확인'}</span> <span className='mono'>{view.sourceRevision}</span></p><p className='n'>게시본 {view.snapshotId} · {view.branch??'범위 미기록'} / {view.docsRoot??'미기록'} · 작성 검사 {view.checklistStatus}</p>
   {view.state==='unchecked'||!view.task?<p>이 게시본의 컨텍스트 자료가 없습니다. 새 수집 자료를 확인하세요.</p>:<>
    <h2>{view.task.item.itemId} {view.task.item.title}</h2><Link to={`/projects/${projectId}/documents/${view.task.item.documentId}?snapshotId=${encodeURIComponent(view.snapshotId)}${view.task.item.anchor?'#'+encodeURIComponent(view.task.item.anchor):''}`}>작업 원문</Link>
    <p>{view.execution?`현재 실행 관찰: ${STATUS_LABELS[view.execution.status]??'미확인'} · Issue ${view.execution.issueNumber??'미등록'} · ${view.execution.observedAt?new Date(view.execution.observedAt).toLocaleString():'관찰 정보 없음'}`:'현재 실행 관찰 미확인'}</p><p>{view.execution?.pullRequests?.map(n=><a key={n} href={`https://github.com/${project.data!.fullName}/pull/${n}`} target='_blank' rel='noopener noreferrer'>PR#{n} </a>)}</p>
    <h3>관련 항목</h3><ul>{[...view.relatedRequirements,...view.designs,...view.dependencies].map(item=><li key={item.kind+item.itemId}><Link to={`/projects/${projectId}/documents/${item.documentId}?snapshotId=${encodeURIComponent(view.snapshotId)}${item.anchor?'#'+encodeURIComponent(item.anchor):''}`}>{item.itemId} {item.title}</Link></li>)}</ul>
    <h3>정본과 규칙 pin</h3><ul>{view.documents.map(d=><li key={d.documentId}><Link to={d.documentUrl}>{d.title}</Link> · <a href={d.sourceUrl} target='_blank' rel='noopener noreferrer'>기준 revision 원문</a></li>)}</ul><ul>{view.rules.map(r=><li key={r.path}><a href={r.sourceUrl} target='_blank' rel='noopener noreferrer'>{r.path}</a><span className='n'> · {r.available?r.sourceHash:'규칙 자료 미확인'}</span></li>)}</ul>
    {view.task.warnings.length>0&&<ul>{view.task.warnings.map((w,i)=><li key={i}>{w}</li>)}</ul>}{view.task.truncated&&<p role='status'>원문 발췌가 잘렸습니다. 작업 원문을 확인하세요.</p>}
    <h3>복사할 컨텍스트</h3><textarea aria-label='컨텍스트 Markdown' readOnly value={view.markdown} rows={24}/><button type='button' onClick={()=>void copyContext()}>컨텍스트 복사</button>{copy&&<p role='status'>{copy}</p>}
    <h3>관련 진단</h3><ul>{view.findings.map((f,i)=><li key={i}>{f.code} {f.path}:{f.line} {f.message}</li>)}</ul>
   </>}
  </>}
 </section></AppShell>;
}
function Failure({status}:{status:number}){return <p role='alert'>{status===400?'작업 ID와 형식을 확인하세요.':status===404?'작업이 없거나 열람 권한이 없습니다.':status===409?'작업 정의가 중복되었거나 첫 수집을 기다려야 합니다.':status===410?'이 게시본은 제공되지 않습니다.':'컨텍스트를 불러오지 못했습니다.'}</p>;}
