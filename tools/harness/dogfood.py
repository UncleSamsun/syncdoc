"""Read-only harness application checks; no document command is ever executed."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit

HERE = Path(__file__).resolve().parent
VALIDATOR = HERE.parent / "spec-validator/validate.py"
sys.dont_write_bytecode = True

def _validator():
    name="syncdoc_harness_validator"
    if name not in sys.modules:
        spec=importlib.util.spec_from_file_location(name,VALIDATOR)
        module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module)
    return sys.modules[name]

def _git(root,*args):
    environment=dict(os.environ,GIT_OPTIONAL_LOCKS="0")
    result=subprocess.run(["git","-c","core.fsmonitor=false","-C",str(root),*args],capture_output=True,env=environment,timeout=15)
    if result.returncode:raise ValueError("Git source unavailable")
    return result.stdout

def _inside(root,path):
    return path.resolve().is_relative_to(root)

def _visible_lines(text):
    """Conservative root-level candidates, not a substitute for the service AST."""
    fence=None;html=None
    for line in text.splitlines():
        marker=re.match(r"^ {0,3}(`{3,}|~{3,})(.*)$",line)
        if fence:
            if marker and marker[1][0]==fence[0] and len(marker[1])>=fence[1] and not marker[2].strip():fence=None
            yield "";continue
        if marker:fence=(marker[1][0],len(marker[1]));yield "";continue
        if html:
            ending=(html=="blank" and not line.strip()) or (html!="blank" and html in line.lower())
            if ending:html=None
            yield "";continue
        lower=line.lstrip().lower()
        if lower.startswith("<!--"):
            html=None if "-->" in lower else "-->";yield "";continue
        raw=re.match(r"^<(script|style|pre|textarea)(?:\s|>|$)",lower)
        if raw:
            ending=f"</{raw[1]}>"
            html=None if ending in lower else ending;yield "";continue
        if re.match(r"^<(?:/?[a-z][a-z0-9-]*)(?:\s|>|/)",lower):
            html="blank";yield "";continue
        yield line

def _links(path,root):
    text="\n".join(_visible_lines(path.read_text(encoding="utf-8")))
    for value in re.findall(r"\[[^\]]*\]\(([^)]+)\)",text):
        parsed=urlsplit(value.strip().strip("<>"))
        if parsed.scheme or parsed.netloc or not parsed.path:continue
        target=path.parent/unquote(parsed.path)
        if not _inside(root,target):raise ValueError("Local link escapes root")
        yield target.resolve().relative_to(root).as_posix()

def check(root,task_id,context=None):
    root=Path(root).resolve();findings=[]
    def error(code,path="",severity="error"):findings.append({"code":code,"path":path,"severity":severity})
    report={"status":"fail","source":{"headRevision":None,"worktreeDirty":None},"entrypoints":[],"rulePins":[],"taskCandidates":[],"validation":{"status":"미검사","errorCount":0,"unwritten":[]},"contextVerification":{"status":"not_requested"},"findings":findings,"limits":"오프라인 TASK 위치는 후보입니다. 검사와 pin 일치는 내용 품질·승인·충족·실행 권한의 증명이 아닙니다."}
    if not re.fullmatch(r"TASK-(?!000)\d{3}",task_id or ""):error("INVALID_TASK_ID");return report
    if not root.is_dir():error("ROOT_MISSING");return report
    paths=[root/"docs",root/"rules",root/"AGENTS.md",root/"templates"]
    for directory in (root/"docs",root/"rules"):
        if _inside(root,directory) and directory.is_dir():paths.extend(directory.rglob("*"))
    if any(not _inside(root,path) for path in paths):
        for path in paths:
            if not _inside(root,path):error("PATH_OUTSIDE_ROOT",str(path.relative_to(root)))
        return report
    required={"AGENTS.md","rules/project-harness.md"};entry_links=set()
    for rel in tuple(required):
        path=root/rel
        if not path.is_file():error("ENTRYPOINT_MISSING",rel);continue
        try:
            for linked in _links(path,root):
                if linked.startswith("rules/") and Path(linked).suffix in (".md",".json") or linked=="templates/README.md":required.add(linked)
                elif linked.startswith("docs/"):entry_links.add(linked)
        except ValueError:error("PATH_OUTSIDE_ROOT",rel)
    for rel in sorted(required):
        if not _inside(root,root/rel):error("PATH_OUTSIDE_ROOT",rel);continue
        if not (root/rel).is_file():error("RULE_MISSING",rel)
    for rel in sorted(entry_links):
        exists=(root/rel).is_file();report["entrypoints"].append({"path":rel,"available":exists})
        if not exists:error("ENTRYPOINT_MISSING",rel)
    if not entry_links:error("DOCUMENT_ENTRYPOINT_UNDECLARED")
    try:
        head=_git(root,"rev-parse","HEAD").decode().strip();dirty=bool(_git(root,"status","--porcelain","--untracked-files=normal"))
        report["source"]={"headRevision":head,"worktreeDirty":dirty}
    except (ValueError,OSError,subprocess.SubprocessError):error("GIT_SOURCE_UNAVAILABLE");head=None;dirty=None
    for rel in sorted(required):
        try:blob=_git(root,"show",f"{head}:{rel}") if head else None
        except (ValueError,OSError,subprocess.SubprocessError):blob=None
        report["rulePins"].append({"path":rel,"available":blob is not None,"sourceHash":hashlib.sha256(blob).hexdigest() if blob is not None else None})
        if blob is None:error("RULE_NOT_VERSIONED",rel,"warning")
    validator=_validator();validation=validator.validate(root)
    report["validation"]={"status":validation.status,"errorCount":len(validation.errors),"unwritten":validation.unwritten,"scope":"working-tree","validatorHash":hashlib.sha256(VALIDATOR.read_bytes()).hexdigest()}
    if validation.status!="통과":error("VALIDATION_NOT_PASS")
    for path in sorted((root/"docs").rglob("*.md")) if (root/"docs").is_dir() else []:
        doc=validator.parse_document(path,root)
        if doc.type!="tasks" or not re.search(r"(?m)^status:\s*확정\s*$",path.read_text(encoding="utf-8")):continue
        for number,line in enumerate(_visible_lines(path.read_text(encoding="utf-8")),1):
            if re.match(r"^ {0,3}##\s+"+task_id+r"(?:\s|$)",line):report["taskCandidates"].append({"path":doc.rel,"line":number,"identity":"candidate"})
    if len(report["taskCandidates"])!=1:error("TASK_CANDIDATE_NOT_UNIQUE")
    if context is not None:
        source_state=context.get("state")
        outcome={"status":"failed","sourceState":source_state if isinstance(source_state,str) and source_state in ("complete","partial","unchecked") else "unknown"};report["contextVerification"]=outcome
        if context.get("schemaVersion")!=1 or context.get("state") not in ("complete","partial"):error("CONTEXT_UNCHECKED")
        task=context.get("task");item=task.get("item") if isinstance(task,dict) else None
        if not isinstance(item,dict) or item.get("itemId")!=task_id:error("CONTEXT_TASK_MISMATCH")
        elif len(report["taskCandidates"])==1 and item.get("path")!=report["taskCandidates"][0]["path"]:error("CONTEXT_TASK_SOURCE_MISMATCH")
        revision=context.get("sourceRevision")
        if not isinstance(revision,str) or not re.fullmatch(r"[a-f0-9]{40}",revision) or revision!=head:error("CONTEXT_REVISION_MISMATCH")
        if dirty is not False:error("DIRTY_SOURCE")
        pins=context.get("rules",[])
        if not isinstance(pins,list):error("INVALID_RULE_MANIFEST");pins=[]
        paths=[p.get("path") for p in pins if isinstance(p,dict) and isinstance(p.get("path"),str)]
        if len(paths)!=len(pins) or len(paths)!=len(set(paths)) or set(paths)!=required:error("RULE_MANIFEST_MISMATCH")
        expected={p["path"]:p for p in report["rulePins"]}
        for pin in pins:
            if not isinstance(pin,dict) or not isinstance(pin.get("path"),str):continue
            actual=expected.get(pin.get("path"))
            if actual is None:error("UNEXPECTED_RULE_PATH");continue
            if pin.get("available") is not True or actual["available"] is not True or pin.get("sourceHash")!=actual["sourceHash"]:error("RULE_PIN_MISMATCH",pin["path"])
        if not any(f["severity"]=="error" for f in findings):outcome["status"]="pins_verified"
    report["status"]="fail" if any(f["severity"]=="error" for f in findings) else "pass"
    return report

def main(argv=None):
    if hasattr(sys.stdout,"reconfigure"):sys.stdout.reconfigure(encoding="utf-8")
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("root",type=Path);parser.add_argument("--task",required=True);parser.add_argument("--context-json",type=Path)
    args=parser.parse_args(argv)
    try:
        context=json.loads(args.context_json.read_text(encoding="utf-8")) if args.context_json else None
        if args.context_json is not None and not isinstance(context,dict):raise ValueError("Context must be an object")
        report=check(args.root,args.task,context)
    except (OSError,ValueError,TypeError) as error:
        report={"status":"fail","findings":[{"code":"INPUT_UNAVAILABLE","type":type(error).__name__}]}
    print(json.dumps(report,ensure_ascii=False));return 0 if report["status"]=="pass" else 1

if __name__=="__main__":raise SystemExit(main())
