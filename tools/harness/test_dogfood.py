import hashlib
import contextlib
import io
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("dogfood", HERE / "dogfood.py")
dogfood = importlib.util.module_from_spec(spec)
sys.modules["dogfood"] = dogfood
spec.loader.exec_module(dogfood)

class HarnessTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name) / "project"
        shutil.copytree(HERE.parent / "spec-validator/fixtures/ok", self.root)
        (self.root / "AGENTS.md").write_text("[docs](docs/README.md) [overview](docs/overview.md) [harness](rules/project-harness.md)", encoding="utf-8")
        (self.root / "rules/project-harness.md").write_text("[format](spec-format.json) [settings](project-settings.md) [writing](spec-writing.md) [templates](../templates/README.md)", encoding="utf-8")
        (self.root / "templates").mkdir()
        (self.root / "templates/README.md").write_text("templates", encoding="utf-8")
        (self.root / "docs/README.md").write_text("---\nid: DOC-901\ntype: guide\nstatus: 확정\n---\n# 진입점", encoding="utf-8")
        self.git("init", "--initial-branch=main")
        self.git("config", "user.name", "Harness Test")
        self.git("config", "user.email", "harness@example.invalid")
        self.git("config", "core.autocrlf", "false")
        self.git("add", ".")
        self.git("commit", "-m", "fixture")
    def tearDown(self):
        self.temp.cleanup()
    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.root), *args], stderr=subprocess.DEVNULL).decode().strip()
    def run_check(self, context=None):
        return dogfood.check(self.root, "TASK-001", context)
    def context(self):
        pins=self.run_check()["rulePins"]
        return {"schemaVersion":1,"state":"partial","sourceRevision":self.git("rev-parse","HEAD"),"task":{"item":{"itemId":"TASK-001","path":"docs/tasks.md"}},"rules":pins}
    def test_actual_entrypoints_rules_validator_and_files_remain_unchanged(self):
        before={p.relative_to(self.root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in self.root.rglob("*") if p.is_file() and ".git" not in p.parts}
        report=self.run_check()
        self.assertEqual(report["status"],"pass")
        self.assertEqual(report["validation"]["status"],"통과")
        self.assertEqual(report["contextVerification"]["status"],"not_requested")
        self.assertEqual(report["taskCandidates"][0]["path"],"docs/tasks.md")
        after={p.relative_to(self.root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in self.root.rglob("*") if p.is_file() and ".git" not in p.parts}
        self.assertEqual(before,after)
    def test_missing_entrypoint_and_unchecked_validation_do_not_pass(self):
        (self.root / "rules/spec-format.json").unlink()
        report=self.run_check()
        self.assertEqual(report["status"],"fail")
        self.assertEqual(report["validation"]["status"],"미검사")
        self.assertIn("RULE_MISSING",[f["code"] for f in report["findings"]])
    def test_context_matches_git_blob_and_retains_partial_source_state(self):
        report=self.run_check(self.context())
        self.assertEqual(report["status"],"pass")
        self.assertEqual(report["contextVerification"]["status"],"pins_verified")
        self.assertEqual(report["contextVerification"]["sourceState"],"partial")
        self.assertEqual(report["source"]["worktreeDirty"],False)
    def test_wrong_revision_hash_path_and_dirty_tree_are_rejected(self):
        for variant in ("revision","hash","path"):
            context=self.context()
            if variant=="revision":context["sourceRevision"]="0"*40
            elif variant=="hash":context["rules"][0]["sourceHash"]="0"*64
            else:context["rules"][0]["path"]="../outside.txt"
            self.assertEqual(self.run_check(context)["status"],"fail",variant)
        context=self.context();(self.root/"docs/tasks.md").write_text("changed",encoding="utf-8")
        self.assertIn("DIRTY_SOURCE",[f["code"] for f in self.run_check(context)["findings"]])
    def test_fenced_and_html_task_examples_are_not_candidates(self):
        p=self.root/"docs/tasks.md"
        p.write_text(p.read_text(encoding="utf-8")+"\n```md\n## TASK-001 가짜\n```\n\n<div>\n## TASK-001 HTML 가짜\n</div>\n",encoding="utf-8")
        self.assertEqual(len(self.run_check()["taskCandidates"]),1)
    def test_duplicate_manifest_entries_are_not_verified(self):
        context=self.context();context["rules"].append(dict(context["rules"][0]))
        self.assertEqual(self.run_check(context)["status"],"fail")
    def test_legacy_null_task_and_malformed_pin_path_are_structured_failures(self):
        context=self.context();context.update(state="unchecked",task=None)
        self.assertEqual(self.run_check(context)["status"],"fail")
        context=self.context();context["rules"][0]["path"]={"bad":"value"}
        self.assertEqual(self.run_check(context)["status"],"fail")
    def test_same_task_id_with_different_source_path_is_not_verified(self):
        context=self.context();context["task"]["item"]["path"]="docs/foreign.md"
        self.assertEqual(self.run_check(context)["status"],"fail")
    def test_explicit_null_context_file_cannot_become_an_offline_success(self):
        path=Path(self.temp.name)/"context.json";path.write_text("null",encoding="utf-8")
        with contextlib.redirect_stdout(io.StringIO()) as output:
            code=dogfood.main([str(self.root),"--task","TASK-001","--context-json",str(path)])
        self.assertEqual(code,1);self.assertEqual(json.loads(output.getvalue())["status"],"fail")
    def test_raw_html_blocks_keep_task_examples_hidden_across_blank_lines(self):
        for tag in ("script","style","pre","textarea"):
            lines=list(dogfood._visible_lines(f"<{tag}>\n\n## TASK-001 example only\n</{tag}>\n\n## TASK-002 actual"))
            self.assertFalse(any("TASK-001" in line for line in lines),tag)
            self.assertTrue(any("TASK-002" in line for line in lines),tag)
    def test_symlink_escape_is_rejected_before_reading_outside_root(self):
        outside=Path(self.temp.name)/"outside.md";outside.write_text("private",encoding="utf-8")
        link=self.root/"docs/escape.md"
        try:link.symlink_to(outside)
        except OSError:self.skipTest("symlink privilege unavailable")
        report=self.run_check();self.assertEqual(report["status"],"fail")
        self.assertIn("PATH_OUTSIDE_ROOT",[f["code"] for f in report["findings"]])

if __name__=="__main__":unittest.main()
