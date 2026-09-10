"""문서 검증기 테스트.

검사 범위는 rules/validation.md를 따른다. C1 필수 문서 존재, C2 필수 항목 존재
두 가지만 확인하고 그 밖의 내용은 검사하지 않는다.
"""

import unittest
from pathlib import Path

import validate

FIXTURES = Path(__file__).parent / "fixtures"


def run(name):
    root = FIXTURES / name
    return validate.validate(root / "docs", root / "settings.md")


def errors(report, check=None):
    return [f for f in report.findings
            if f.kind == "error" and (check is None or f.check == check)]


class OkFixture(unittest.TestCase):
    def test_passes_with_no_errors(self):
        report = run("ok")
        self.assertEqual(errors(report), [])
        self.assertEqual(report.status, "통과")

    def test_held_type_without_document_is_reported_as_unwritten(self):
        report = run("ok")
        self.assertIn("tech-ops", report.unwritten)
        self.assertEqual(errors(report), [])

    def test_type_marked_not_applicable_is_not_reported(self):
        report = run("ok")
        self.assertNotIn("tech-data", report.unwritten)

    def test_guide_type_skips_required_label_check(self):
        report = run("ok")
        self.assertEqual([f for f in errors(report, "C2")
                          if f.file.endswith("memo.md")], [])


class C1RequiredDocuments(unittest.TestCase):
    def test_applied_type_without_document_is_error(self):
        report = run("bad-c1")
        messages = [f.message for f in errors(report, "C1")]
        self.assertTrue(any("ui-screens" in m for m in messages), messages)

    def test_held_type_without_reason_is_error(self):
        report = run("bad-c1")
        messages = [f.message for f in errors(report, "C1")]
        self.assertTrue(any("tech-ops" in m and "사유" in m for m in messages),
                        messages)

    def test_document_without_frontmatter_is_error(self):
        report = run("bad-c1")
        files = [f.file for f in errors(report, "C1")]
        self.assertTrue(any(f.endswith("nofm.md") for f in files), files)

    def test_retired_note_type_is_error(self):
        report = run("bad-c1")
        found = [f for f in errors(report, "C1")
                 if f.file.endswith("oldtype.md")]
        self.assertTrue(found, [str(f) for f in errors(report, "C1")])

    def test_type_absent_from_settings_table_is_error(self):
        report = run("bad-c1")
        files = [f.file for f in errors(report, "C1")]
        self.assertTrue(any(f.endswith("weird.md") for f in files), files)


class C2RequiredItems(unittest.TestCase):
    def test_missing_required_label_is_error(self):
        report = run("bad-c2")
        found = [f for f in errors(report, "C2")
                 if f.file.endswith("req.md") and "인수 기준" in f.message]
        self.assertTrue(found, [f.message for f in errors(report, "C2")])

    def test_label_without_content_on_same_line_is_error(self):
        report = run("bad-c2")
        found = [f for f in errors(report, "C2")
                 if f.file.endswith("screens.md") and "연결 요구" in f.message]
        self.assertTrue(found, [f.message for f in errors(report, "C2")])

    def test_document_with_no_check_unit_is_error(self):
        report = run("bad-c2")
        found = [f for f in errors(report, "C2")
                 if f.file.endswith("tasks.md")]
        self.assertTrue(found, [f.message for f in errors(report, "C2")])

    def test_error_points_at_the_check_unit_line(self):
        report = run("bad-c2")
        found = [f for f in errors(report, "C2")
                 if f.file.endswith("req.md")][0]
        self.assertEqual(found.line, 9)


class MissingSettings(unittest.TestCase):
    def test_reports_unchecked_when_applied_spec_table_is_absent(self):
        report = run("no-settings")
        self.assertEqual(report.status, "미검사")

    def test_does_not_report_errors_when_unchecked(self):
        report = run("no-settings")
        self.assertEqual(errors(report), [])


class OutOfScope(unittest.TestCase):
    """rules/validation.md 2절이 검사하지 않겠다고 정한 것."""

    def test_extra_content_beyond_required_items_is_not_an_error(self):
        report = run("ok")
        self.assertEqual(errors(report), [])

    def test_broken_relative_link_is_not_checked(self):
        root = FIXTURES / "ok"
        doc = root / "docs" / "req.md"
        original = doc.read_text(encoding="utf-8")
        doc.write_text(original + "\n[없는 문서](./nowhere.md)\n",
                       encoding="utf-8")
        try:
            report = validate.validate(root / "docs", root / "settings.md")
            self.assertEqual(errors(report), [])
        finally:
            doc.write_text(original, encoding="utf-8")


if __name__ == "__main__":
    unittest.main(verbosity=2)
