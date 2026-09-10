"""문서 검증기.

rules/validation.md의 검사 두 가지만 확인한다.

  C1 필수 문서 존재 — 적용한다고 정한 문서 종류마다 그 종류의 문서가 있는지
  C2 필수 항목 존재 — 각 문서에 그 종류의 필수 항목이 있는지

그 밖의 내용, 절 순서, 분량, 링크·앵커·ID는 검사하지 않는다.
검사 항목을 늘릴 때는 rules/validation.md를 먼저 고친다.

사용법:
    python tools/spec-validator/validate.py [DOCS_DIR] [SETTINGS_FILE]
"""

import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

# rules/spec-writing.md 3절의 type 허용 값
ALLOWED_TYPES = (
    # 명세 문서 — 구현 기준이 될 수 있다
    "prd-overview", "prd-requirements",
    "ui-conventions", "ui-screens",
    "tech-overview", "tech-interface", "tech-data", "tech-ops",
    "tasks",
    # 명세가 아닌 문서 — status와 무관하게 구현 기준이 아니다
    "proposal", "record", "guide",
)

# rules/spec-writing.md 5절의 검사 라벨 표. 이 표를 고치면 그 문서도 함께 고친다.
REQUIRED_LABELS = {
    "prd-requirements": ("REQ", ("인수 기준",)),
    "ui-screens": ("UI", ("연결 요구", "검증")),
    "tasks": ("TASK", ("근거", "완료")),
}

APPLY_VALUES = ("적용", "보류", "미적용")

_FRONTMATTER = re.compile(r"\A---\r?\n(.*?)\r?\n---\r?\n", re.S)
_TABLE_ROW = re.compile(r"^\|(.+)\|\s*$")


@dataclass(frozen=True)
class Finding:
    kind: str      # "error"
    check: str     # "C1" | "C2"
    file: str
    line: int
    message: str

    def __str__(self):
        return "%s:%d [%s] %s" % (self.file, self.line, self.check, self.message)


@dataclass
class Report:
    findings: list = field(default_factory=list)
    unwritten: list = field(default_factory=list)
    status: str = "통과"

    @property
    def errors(self):
        return [f for f in self.findings if f.kind == "error"]


@dataclass
class Document:
    path: Path
    rel: str
    lines: list
    type: str = None


def _read_lines(path):
    return path.read_text(encoding="utf-8").splitlines()


def parse_applied_spec(settings_file):
    """적용 Spec 표를 읽어 {type: (적용값, 사유, 줄번호)}로 돌려준다.

    표가 없으면 None. 미검사 판정에 쓴다.
    """
    if not settings_file.is_file():
        return None
    lines = _read_lines(settings_file)
    start = None
    for i, line in enumerate(lines):
        if line.strip().startswith("## ") and "적용 Spec" in line:
            start = i
            break
    if start is None:
        return None

    table = {}
    for i in range(start + 1, len(lines)):
        line = lines[i]
        if line.strip().startswith("## "):
            break
        m = _TABLE_ROW.match(line)
        if not m:
            continue
        cells = [c.strip() for c in m.group(1).split("|")]
        if len(cells) < 3:
            continue
        name, apply_value, reason = cells[0], cells[1], cells[2]
        if name in ("type", "") or set(name) <= set("-: "):
            continue
        table[name] = (apply_value, reason, i + 1)
    return table or None


def parse_document(path, docs_dir):
    rel = path.relative_to(docs_dir.parent).as_posix()
    text = path.read_text(encoding="utf-8")
    doc = Document(path=path, rel=rel, lines=text.splitlines())
    m = _FRONTMATTER.match(text)
    if m:
        for row in m.group(1).splitlines():
            key, sep, value = row.partition(":")
            if sep and key.strip() == "type":
                doc.type = value.strip()
    return doc


def check_units(doc, prefix):
    """`## PREFIX-NNN` 제목의 (줄번호, 제목, 본문줄들)을 돌려준다."""
    heading = re.compile(r"^##\s+(%s-\d+)\b" % re.escape(prefix))
    starts = [(i, m.group(1)) for i, line in enumerate(doc.lines)
              for m in [heading.match(line)] if m]
    units = []
    for n, (i, name) in enumerate(starts):
        end = starts[n + 1][0] if n + 1 < len(starts) else len(doc.lines)
        units.append((i + 1, name, doc.lines[i:end]))
    return units


def has_label_with_content(body, label):
    pattern = re.compile(r"\*\*%s\s*:\*\*\s*(\S.*)?$" % re.escape(label))
    for line in body:
        m = pattern.search(line)
        if m:
            return bool(m.group(1) and m.group(1).strip())
    return False


def validate(docs_dir, settings_file):
    docs_dir = Path(docs_dir)
    settings_file = Path(settings_file)
    report = Report()

    table = parse_applied_spec(settings_file)
    if table is None:
        report.status = "미검사"
        return report

    paths = sorted(p for p in docs_dir.rglob("*.md")) if docs_dir.is_dir() else []
    if not paths:
        report.status = "미검사"
        return report

    docs = [parse_document(p, docs_dir) for p in paths]
    settings_rel = settings_file.as_posix()

    # C1 — 문서 분류
    for doc in docs:
        if doc.type is None:
            report.findings.append(Finding(
                "error", "C1", doc.rel, 1,
                "frontmatter의 type이 없어 문서를 분류할 수 없다"))
        elif doc.type not in ALLOWED_TYPES:
            report.findings.append(Finding(
                "error", "C1", doc.rel, 1,
                "type '%s'은 허용 값이 아니다" % doc.type))
        elif doc.type not in table:
            report.findings.append(Finding(
                "error", "C1", doc.rel, 1,
                "type '%s'이 적용 Spec 표에 없다" % doc.type))

    # C1 — 필수 문서 존재
    present = {doc.type for doc in docs if doc.type}
    for name, (apply_value, reason, line) in sorted(table.items()):
        if apply_value not in APPLY_VALUES:
            report.findings.append(Finding(
                "error", "C1", settings_rel, line,
                "'%s'의 적용 값 '%s'은 허용 값이 아니다" % (name, apply_value)))
            continue
        if apply_value in ("보류", "미적용") and not reason:
            report.findings.append(Finding(
                "error", "C1", settings_rel, line,
                "'%s'을 %s로 두었으나 사유가 없다" % (name, apply_value)))
        if apply_value == "적용" and name not in present:
            report.findings.append(Finding(
                "error", "C1", settings_rel, line,
                "'%s' 문서가 없다" % name))
        elif apply_value == "보류" and name not in present:
            report.unwritten.append(name)

    # C2 — 필수 항목 존재
    for doc in docs:
        spec = REQUIRED_LABELS.get(doc.type)
        if spec is None:
            continue
        prefix, labels = spec
        units = check_units(doc, prefix)
        if not units:
            report.findings.append(Finding(
                "error", "C2", doc.rel, 1,
                "검사 단위 '## %s-NNN' 섹션이 하나도 없다" % prefix))
            continue
        for line, name, body in units:
            for label in labels:
                if not has_label_with_content(body, label):
                    report.findings.append(Finding(
                        "error", "C2", doc.rel, line,
                        "%s에 '**%s:**' 항목이 없다" % (name, label)))

    if report.errors:
        report.status = "오류"
    return report


def main(argv):
    docs_dir = Path(argv[0]) if len(argv) > 0 else Path("docs")
    settings = Path(argv[1]) if len(argv) > 1 else Path("rules/project-settings.md")
    report = validate(docs_dir, settings)

    for finding in report.findings:
        print(finding)
    if report.unwritten:
        print("미작성: %s" % ", ".join(sorted(report.unwritten)))
    print("상태: %s (오류 %d건)" % (report.status, len(report.errors)))
    return 1 if report.errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
