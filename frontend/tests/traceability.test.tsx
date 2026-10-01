import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, expect, it, vi } from "vitest";
import TraceabilityPanel from "../src/features/spec/TraceabilityPanel";
const item = { itemId:"REQ-001",title:"로그인",documentId:"reqdoc",path:"docs/req.md",anchor:"actual-anchor",line:9 };
const view = {snapshotId:"s1",sourceRevision:"abc123",analysisVersion:1,analysisStatus:"partial",uncheckedReason:null,requirements:[{item,coverage:"unknown",tasks:[]}],totalElements:51,page:0,size:50};
function mock(report: unknown = view) { vi.stubGlobal("fetch",vi.fn((url:string)=>Promise.resolve(new Response(JSON.stringify(String(url).includes("/findings")?{...view,findings:[{code:"MISSING_REQUIREMENT",severity:"error",documentId:"taskdoc",path:"docs/tasks.md",line:12,itemId:"TASK-001",targetId:"REQ-099",message:"참조한 요구 정의가 없습니다."}],totalElements:1}:report))))); }
function open(snapshotId="s1") { return render(<MemoryRouter><TraceabilityPanel projectId="p1" snapshotId={snapshotId} fullName="owner/repo" /></MemoryRouter>); }
afterEach(()=>vi.unstubAllGlobals());
it("pins both requests and actual document anchors and keeps partial coverage unknown",async()=>{
 mock();open();await screen.findByText("부분 분석");
 expect(screen.getAllByText("판정 보류").length).toBeGreaterThan(0);
 expect(screen.getByRole("link",{name:/REQ-001/})).toHaveAttribute("href","/projects/p1/documents/reqdoc?snapshotId=s1#actual-anchor");
 expect(screen.getByText("docs/tasks.md:12")).toBeInTheDocument();
 expect(vi.mocked(fetch).mock.calls.every(([url])=>String(url).includes("snapshotId=s1"))).toBe(true);
 await userEvent.selectOptions(screen.getByLabelText("요구 연결 상태"),"linked");
 await waitFor(()=>expect(vi.mocked(fetch).mock.calls.some(([url])=>String(url).includes("coverage=linked"))).toBe(true));
});
it("shows unchecked reason instead of empty success",async()=>{
 mock({...view,analysisVersion:null,analysisStatus:"unchecked",uncheckedReason:"NOT_COMPUTED",requirements:[],totalElements:0});open();
 await screen.findByText("미분석");expect(screen.getByText(/분석 기능 도입 전/)).toBeInTheDocument();
});
it.each([[409,"첫 수집"],[404,"권한"],[410,"회수"],[500,"불러오지"]])("distinguishes HTTP %s",async(status,text)=>{
 vi.stubGlobal("fetch",vi.fn(()=>Promise.resolve(new Response(JSON.stringify({code:"TEST"}),{status:Number(status)}))));open();await screen.findByText(new RegExp(String(text)));
});
it("excludes a late response for the old snapshot",async()=>{
 const old: ((response:Response)=>void)[]=[];
 vi.stubGlobal("fetch",vi.fn((url:string)=>String(url).includes("snapshotId=old")?new Promise<Response>(resolve=>old.push(resolve)):Promise.resolve(new Response(JSON.stringify(String(url).includes("/findings")?{...view,snapshotId:"new",findings:[],totalElements:0}:{...view,snapshotId:"new"})))));
 const current=open("old");current.rerender(<MemoryRouter><TraceabilityPanel projectId="p1" snapshotId="new" fullName="owner/repo" /></MemoryRouter>);
 await screen.findByText(/abc123/);old.forEach(resolve=>resolve(new Response(JSON.stringify({...view,snapshotId:"old",sourceRevision:"OLDREV"}))));
 await waitFor(()=>expect(screen.queryByText(/OLDREV/)).toBeNull());
 expect(screen.getByRole("link",{name:/REQ-001/})).toHaveAttribute("href",expect.stringContaining("snapshotId=new"));
});
