import { render,screen,waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {MemoryRouter,Routes,Route,useNavigate} from "react-router-dom";
import {afterEach,expect,it,vi} from "vitest";
import ComparisonPage from "../src/features/spec/ComparisonPage";
const project={id:"p1",fullName:"owner/repo",branch:"main",docsRoot:"docs",currentSnapshotId:"b",version:1,syncState:"succeeded",manageable:false};
const snapshot=(id:string)=>({snapshotId:id,sourceRevision:id.repeat(8),createdAt:"2026-10-02T00:00:00Z",rendererVersion:"r",policyVersion:"p",branch:"main",docsRoot:"docs",current:id==="b",comparisonReadiness:"ready"});
const meta={from:snapshot("a"),to:snapshot("b"),status:"complete",reason:null,coverage:"complete",findings:[]};
const ref=(doc:string)=>({documentId:doc,specId:"DOC-001",path:"docs/a.md",title:"본문만 변경",kind:"req",itemId:"REQ-001",anchor:"real-anchor",line:10,status:"확정"});
function mock(legacy=false){vi.stubGlobal("fetch",vi.fn((url:string)=>{
 const u=String(url);let data:unknown=project;
 if(u.includes("/snapshots"))data={items:[snapshot("b"),snapshot("a")],totalElements:2,page:0,size:20};
 else if(u.includes("/documents")&&!u.includes("snapshot-comparison"))data={snapshotId:"b",items:[],nextCursor:null};
 else if(u.includes("/snapshot-comparison"))data=u.includes("/snapshot-comparison?")?{...meta,status:legacy?"unchecked":"complete",reason:legacy?"COMPARISON_INDEX_UNAVAILABLE":null,counts:legacy?null:{documents:{modified:1},items:{modified:1}}}:{...meta,status:legacy?"unchecked":"complete",items:legacy?[]:[{key:"req:REQ-001",kind:"req",change:"modified",reason:null,before:ref("old-doc"),after:ref("new-doc")}],totalElements:1,page:0,size:50};
 return Promise.resolve(new Response(JSON.stringify(data)));
}));}
function open(search="?fromSnapshotId=a&toSnapshotId=b"){return render(<MemoryRouter initialEntries={["/projects/p1/comparison"+search]}><Routes><Route path="/projects/:projectId/comparison" element={<ComparisonPage/>}/></Routes></MemoryRouter>);}
afterEach(()=>vi.unstubAllGlobals());
it("pins design impact sources and shows missing relation data as unchecked",async()=>{
 mock();const original=vi.mocked(fetch).getMockImplementation()!;
 vi.mocked(fetch).mockImplementation((u,...args)=>String(u).includes('/design-impacts')?Promise.resolve(new Response(JSON.stringify({...meta,items:[{requirementId:'REQ-001',requirementChange:'modified',kind:'ui',itemId:'UI-001',before:{...ref('old-ui'),kind:'ui',itemId:'UI-001'},after:{...ref('new-ui'),kind:'ui',itemId:'UI-001'},presence:'present'}],totalElements:1,page:0,size:50}))):original(u,...args));
 open();await screen.findByText('전 범위 비교');await userEvent.click(screen.getByRole('button',{name:'설계 재검토'}));
 const links=await screen.findAllByRole('link',{name:/UI-001/});expect(links[0]).toHaveAttribute('href','/projects/p1/documents/old-ui?snapshotId=a#real-anchor');expect(links[1]).toHaveAttribute('href','/projects/p1/documents/new-ui?snapshotId=b#real-anchor');
});
it("pins both source links and filter requests to the same pair",async()=>{
 mock();open();await screen.findByText("전 범위 비교");
 const links=screen.getAllByRole("link",{name:/REQ-001/});
 expect(links[0]).toHaveAttribute("href","/projects/p1/documents/old-doc?snapshotId=a#real-anchor");
 expect(links[1]).toHaveAttribute("href","/projects/p1/documents/new-doc?snapshotId=b#real-anchor");
 await userEvent.selectOptions(screen.getByLabelText("변경 종류"),"modified");
 await waitFor(()=>expect(vi.mocked(fetch).mock.calls.some(([u])=>String(u).includes("change=modified"))).toBe(true));
});
it("shows legacy as unavailable comparison rather than unchanged zero",async()=>{mock(true);open();await screen.findByText("비교 미확인");expect(screen.getByText(/비교 자료/)).toBeInTheDocument();expect(screen.queryByText("변경이 없습니다.")).toBeNull();});
it("offers defaults but allows selection from paginated snapshot metadata",async()=>{mock();open("");await screen.findByText("전 범위 비교");expect(screen.getByLabelText("기준 게시본")).toHaveValue("a");expect(screen.getByLabelText("대상 게시본")).toHaveValue("b");});
it("does not publish an old pair after the user changes the selection",async()=>{
 mock();const original=vi.mocked(fetch).getMockImplementation()!;const waiting:((r:Response)=>void)[]=[];
 vi.mocked(fetch).mockImplementation((u,...args)=>String(u).includes("snapshot-comparison")&&String(u).includes("fromSnapshotId=a")?new Promise<Response>(resolve=>waiting.push(resolve)):original(u,...args));
 open();await screen.findByLabelText("기준 게시본");await userEvent.selectOptions(screen.getByLabelText("기준 게시본"),"b");
 waiting.forEach(resolve=>resolve(new Response(JSON.stringify({...meta,status:"partial",counts:null}))));
 await waitFor(()=>expect(screen.queryByText("부분 비교")).toBeNull());
});

it.each([[409,"같은 수집"],[410,"회수"],[404,"권한"],[400,"조건"],[500,"불러오지"]])("distinguishes comparison HTTP %s",async(status,text)=>{
 mock();const original=vi.mocked(fetch).getMockImplementation()!;
 vi.mocked(fetch).mockImplementation((u,...args)=>String(u).includes("snapshot-comparison")?Promise.resolve(new Response(JSON.stringify({code:"TEST"}),{status:Number(status)})):original(u,...args));
 open();await screen.findAllByText(new RegExp(String(text)));
});
it("keeps the chosen pair when paging snapshot options",async()=>{
 mock();const original=vi.mocked(fetch).getMockImplementation()!;
 vi.mocked(fetch).mockImplementation((u,...args)=>String(u).includes("/snapshots")?Promise.resolve(new Response(JSON.stringify({items:String(u).includes("page=1")?[snapshot("c")]:[snapshot("b"),snapshot("a")],totalElements:21,page:0,size:20}))):original(u,...args));
 open();await screen.findByText("전 범위 비교");await userEvent.click(screen.getByRole("button",{name:"게시본 목록 다음"}));
 await waitFor(()=>expect(screen.getByLabelText("기준 게시본")).toHaveValue("a"));expect(screen.getByLabelText("대상 게시본")).toHaveValue("b");
 await waitFor(()=>expect(screen.getAllByRole("option").some(o=>o.textContent?.includes("cccccccc"))).toBe(true));
});
it("shows removed tasks without inventing a current Issue state",async()=>{
 mock();const original=vi.mocked(fetch).getMockImplementation()!;
 vi.mocked(fetch).mockImplementation((u,...args)=>String(u).includes("/impacts")?Promise.resolve(new Response(JSON.stringify({...meta,items:[{requirementId:"REQ-001",taskId:"TASK-001",requirementChange:"modified",beforeRequirement:ref("old-doc"),afterRequirement:ref("new-doc"),beforeTask:{...ref("task-doc"),itemId:"TASK-001"},afterTask:null,taskPresence:"removed",execution:null}],totalElements:1,page:0,size:50}))):original(u,...args));
 open();await screen.findByText("전 범위 비교");await userEvent.click(screen.getByRole("button",{name:"재검토 후보"}));await screen.findByText("현재 실행 상태 미확인");
 expect(screen.getByRole("link",{name:/TASK-001/})).toHaveAttribute("href",expect.stringContaining("snapshotId=a"));
});

it("resets result pagination when browser history returns to another pair",async()=>{
 mock();const original=vi.mocked(fetch).getMockImplementation()!;
 vi.mocked(fetch).mockImplementation((u,...args)=>{
  const url=String(u);if(!url.includes("snapshot-comparison"))return original(u,...args);
  const next=url.includes("fromSnapshotId=b");const m={...meta,from:snapshot(next?"b":"a")};
  return Promise.resolve(new Response(JSON.stringify(url.includes("/snapshot-comparison?")?{...m,counts:{documents:{modified:1},items:{modified:1}}}:{...m,items:url.includes("page=1")?[]:[{key:"req:REQ-001",kind:"req",change:"modified",reason:null,before:ref("old-doc"),after:ref("new-doc")}],totalElements:next?51:1,page:0,size:50})));
 });
 function Back(){const navigate=useNavigate();return <button onClick={()=>navigate(-1)}>브라우저 뒤로</button>;}
 render(<MemoryRouter initialEntries={["/projects/p1/comparison?fromSnapshotId=a&toSnapshotId=b"]}><Back/><Routes><Route path="/projects/:projectId/comparison" element={<ComparisonPage/>}/></Routes></MemoryRouter>);
 await screen.findByText("전 범위 비교");await userEvent.selectOptions(screen.getByLabelText("기준 게시본"),"b");
 await waitFor(()=>expect(screen.getByRole("button",{name:"비교 결과 다음"})).not.toBeDisabled());
 await userEvent.click(screen.getByRole("button",{name:"비교 결과 다음"}));await userEvent.click(screen.getByRole("button",{name:"브라우저 뒤로"}));
 await waitFor(()=>expect(screen.getByLabelText("기준 게시본")).toHaveValue("a"));
 await waitFor(()=>expect(screen.getByRole("navigation",{name:"비교 결과 페이지"})).toHaveTextContent("1 / 1"));
});
