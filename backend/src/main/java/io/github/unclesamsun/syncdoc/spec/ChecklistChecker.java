package io.github.unclesamsun.syncdoc.spec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * C1 필수 문서 존재와 C2 필수 항목 존재를 판정한다.
 *
 * <p>판정 기준은 [검증 규칙](../../rules/validation.md)이고, 저장소의 검사기
 * (`tools/spec-validator/validate.py`)와 같은 규칙을 따른다. 두 구현의 판정이 갈리면 규칙 문서를
 * 기준으로 고친다. 오류 문구도 검사기와 같게 둔다. 같은 오류를 두 곳에서 다르게 부르면 고칠 곳을
 * 찾는 사람이 둘을 다른 문제로 읽는다.
 *
 * <p>C0 정의 파일 검사는 하지 않는다. 저장소에서 규칙 표를 고치는 사람을 위한 검사이고, 서비스가
 * 읽는 정본은 정의 파일 하나다.
 */
@Component
public class ChecklistChecker {

    /** 종류 하나에 담는 오류 상한. 넘으면 자르고 잘렸다고 알린다. */
    public static final int MAX_FINDINGS_PER_TYPE = 50;
    /** 전체 오류 상한. 이보다 많은 상태에서 필요한 것은 다음 쪽이 아니라 저장소에서 고치는 일이다. */
    public static final int MAX_FINDINGS = 500;

    private static final String SETTINGS_PATH = "rules/project-settings.md";
    private static final Pattern DOC_ID = Pattern.compile("\\ADOC-\\d{3}\\z");
    private static final Pattern FRONTMATTER =
            Pattern.compile("\\A---\\r?\\n(.*?)\\r?\\n---\\r?\\n", Pattern.DOTALL);
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|\\s*$");

    /**
     * 판정에 넣을 문서 하나.
     *
     * @param documentId 게시본에 저장한 문서 id. 오류에서 그 문서로 이어 가는 데 쓴다
     * @param path       저장소 기준 경로
     * @param markdown   원문. 라벨과 표는 변환 결과가 아니라 원문에서 본다
     */
    public record SourceDocument(String documentId, String path, String markdown) {
    }

    public SpecChecklist check(SpecFormat format, ApplySpecTable table, List<SourceDocument> sources) {
        if (format == null) {
            return SpecChecklist.unchecked(SpecChecklist.DEFINITION_MISSING);
        }
        if (table == null) {
            return SpecChecklist.unchecked(SpecChecklist.APPLY_TABLE_MISSING);
        }
        if (sources.isEmpty()) {
            // 검사할 문서가 없다. 오류 0건을 통과로 보이게 하지 않는다.
            return SpecChecklist.unchecked(SpecChecklist.NO_DOCUMENTS);
        }

        List<Parsed> documents = sources.stream()
                .sorted(Comparator.comparing(SourceDocument::path))
                .map(Parsed::of)
                .toList();
        Map<String, ApplySpecTable.Applied> applied = table.byType();

        List<SpecChecklist.Finding> loose = new ArrayList<>();
        loose.addAll(identity(documents));
        loose.addAll(classification(documents, format, applied));

        Map<String, List<SpecChecklist.Finding>> byType = new LinkedHashMap<>();
        Map<String, List<SpecChecklist.DocumentRef>> docsByType = new LinkedHashMap<>();
        for (ApplySpecTable.Applied row : table.rows()) {
            byType.put(row.type(), new ArrayList<>());
            docsByType.put(row.type(), new ArrayList<>());
        }
        for (Parsed document : documents) {
            if (document.type == null) {
                continue;
            }
            List<SpecChecklist.DocumentRef> refs = docsByType.get(document.type);
            if (refs != null) {
                refs.add(new SpecChecklist.DocumentRef(document.documentId, document.path, document.id));
            }
        }

        // C1 필수 문서 존재
        List<String> pending = new ArrayList<>();
        for (ApplySpecTable.Applied row : table.rows()) {
            List<SpecChecklist.Finding> findings = byType.get(row.type());
            boolean present = documents.stream().anyMatch(doc -> row.type().equals(doc.type));
            if (!row.isKnownValue()) {
                findings.add(settingsFinding(row.line(),
                        "'%s'의 적용 값 '%s'은 허용 값이 아니다".formatted(row.type(), row.apply())));
                continue;
            }
            if (row.needsReason() && row.reason().isEmpty()) {
                findings.add(settingsFinding(row.line(),
                        "'%s'을 %s로 두었으나 사유가 없다".formatted(row.type(), row.apply())));
            }
            if (ApplySpecTable.APPLIED.equals(row.apply()) && !present) {
                findings.add(settingsFinding(row.line(), "'%s' 문서가 없다".formatted(row.type())));
            } else if (ApplySpecTable.DEFERRED.equals(row.apply()) && !present) {
                pending.add(row.type());
            }
        }

        // C2 필수 항목 존재. 정의 파일의 checks가 무엇을 볼지 정한다.
        for (Parsed document : documents) {
            if (document.type == null) {
                // 분류할 수 없는 문서다. 어느 종류의 필수 항목을 요구할지 알 수 없다.
                continue;
            }
            SpecFormat.DocumentType type = format.types().get(document.type);
            List<SpecChecklist.Finding> findings = byType.get(document.type);
            if (type == null || findings == null) {
                continue;
            }
            for (SpecFormat.Check check : type.checks()) {
                findings.addAll(switch (check.unit()) {
                    case SpecFormat.Check.DOCUMENT -> documentLabels(document, check.labels());
                    case SpecFormat.Check.SECTION -> sectionLabels(document, check.idPrefix(),
                            check.labels());
                    case SpecFormat.Check.TABLE -> table(document, check);
                    default -> List.of();
                });
            }
        }

        return assemble(format, table, applied, loose, byType, docsByType, pending);
    }

    private SpecChecklist assemble(SpecFormat format, ApplySpecTable table,
                                   Map<String, ApplySpecTable.Applied> applied,
                                   List<SpecChecklist.Finding> loose,
                                   Map<String, List<SpecChecklist.Finding>> byType,
                                   Map<String, List<SpecChecklist.DocumentRef>> docsByType,
                                   List<String> pending) {
        boolean[] truncated = {false};
        int[] budget = {MAX_FINDINGS};
        List<SpecChecklist.Finding> looseCut = cut(loose, budget, truncated);

        List<SpecChecklist.TypeResult> types = new ArrayList<>();
        boolean anyError = !loose.isEmpty();
        for (ApplySpecTable.Applied row : table.rows()) {
            List<SpecChecklist.Finding> findings = byType.getOrDefault(row.type(), List.of());
            boolean notApplied = ApplySpecTable.NOT_APPLIED.equals(row.apply()) && row.isKnownValue();
            String status;
            if (!findings.isEmpty()) {
                status = SpecChecklist.ERROR;
                anyError = true;
            } else if (notApplied) {
                // 검사하지 않은 종류다. 상태를 비워 둔다. 통과로 보이게 하지 않는다.
                status = null;
            } else if (pending.contains(row.type())) {
                status = SpecChecklist.PENDING;
            } else {
                status = SpecChecklist.PASS;
            }
            types.add(new SpecChecklist.TypeResult(row.type(), format.nameOf(row.type()), row.apply(),
                    row.reason(), status, docsByType.getOrDefault(row.type(), List.of()),
                    cut(findings, budget, truncated)));
        }

        String status = anyError ? SpecChecklist.ERROR
                : pending.isEmpty() ? SpecChecklist.PASS : SpecChecklist.PENDING;
        return new SpecChecklist(status, null, truncated[0], looseCut, types);
    }

    /** 상한까지만 담고, 자른 것이 있으면 알린다. 조용히 짧아진 목록은 통과처럼 읽힌다. */
    private static List<SpecChecklist.Finding> cut(List<SpecChecklist.Finding> findings, int[] budget,
                                                   boolean[] truncated) {
        int limit = Math.min(Math.min(findings.size(), MAX_FINDINGS_PER_TYPE), Math.max(budget[0], 0));
        if (limit < findings.size()) {
            truncated[0] = true;
        }
        budget[0] -= limit;
        return List.copyOf(findings.subList(0, limit));
    }

    private static List<SpecChecklist.Finding> identity(List<Parsed> documents) {
        List<SpecChecklist.Finding> findings = new ArrayList<>();
        Map<String, String> seen = new HashMap<>();
        for (Parsed document : documents) {
            if (document.id == null) {
                findings.add(document.finding("C1", "frontmatter의 id가 없어 문서를 참조할 수 없다"));
            } else if (!DOC_ID.matcher(document.id).matches()) {
                findings.add(document.finding("C1",
                        "id '%s'은 DOC-NNN 형식이 아니다".formatted(document.id)));
            } else if (seen.containsKey(document.id)) {
                findings.add(document.finding("C1",
                        "id '%s'이 %s와 중복된다".formatted(document.id, seen.get(document.id))));
            } else {
                seen.put(document.id, document.path);
            }
        }
        return findings;
    }

    private static List<SpecChecklist.Finding> classification(List<Parsed> documents, SpecFormat format,
                                                              Map<String, ApplySpecTable.Applied> applied) {
        List<SpecChecklist.Finding> findings = new ArrayList<>();
        for (Parsed document : documents) {
            if (document.type == null) {
                findings.add(document.finding("C1", "frontmatter의 type이 없어 문서를 분류할 수 없다"));
            } else if (!format.types().containsKey(document.type)) {
                findings.add(document.finding("C1",
                        "type '%s'은 허용 값이 아니다".formatted(document.type)));
            } else if (!applied.containsKey(document.type)) {
                findings.add(document.finding("C1",
                        "type '%s'이 적용 Spec 표에 없다".formatted(document.type)));
            }
        }
        return findings;
    }

    private static List<SpecChecklist.Finding> documentLabels(Parsed document, List<String> labels) {
        List<SpecChecklist.Finding> findings = new ArrayList<>();
        for (String label : labels) {
            if (!hasLabelWithContent(document.lines, label)) {
                findings.add(document.finding("C2", "'**%s:**' 항목이 없다".formatted(label)));
            }
        }
        return findings;
    }

    private static List<SpecChecklist.Finding> sectionLabels(Parsed document, String idPrefix,
                                                             List<String> labels) {
        List<Section> sections = sections(document.lines, idPrefix);
        if (sections.isEmpty()) {
            return List.of(document.finding("C2",
                    "검사 단위 '## %s-NNN' 섹션이 하나도 없다".formatted(idPrefix)));
        }
        List<SpecChecklist.Finding> findings = new ArrayList<>();
        for (Section section : sections) {
            for (String label : labels) {
                if (!hasLabelWithContent(section.body, label)) {
                    findings.add(document.finding("C2", section.line,
                            "%s에 '**%s:**' 항목이 없다".formatted(section.name, label)));
                }
            }
        }
        return findings;
    }

    private static List<SpecChecklist.Finding> table(Parsed document, SpecFormat.Check check) {
        MarkdownTable table = MarkdownTable.under(document.lines, check.heading());
        if (table == null) {
            return List.of(document.finding("C2",
                    "'## %s' 표가 없어 행을 셀 수 없다".formatted(check.heading())));
        }
        List<String> absent = check.columns().stream()
                .filter(column -> !table.columns.contains(column))
                .toList();
        if (!absent.isEmpty()) {
            return absent.stream()
                    .map(column -> document.finding("C2", table.headerLine,
                            "'%s' 표에 '%s' 열이 없다".formatted(check.heading(), column)))
                    .toList();
        }
        if (table.rows.isEmpty()) {
            return List.of(document.finding("C2", table.headerLine,
                    "'%s' 표에 행이 하나도 없다".formatted(check.heading())));
        }

        boolean hasId = check.columns().contains("ID");
        Pattern idPattern = Pattern.compile(
                "\\A" + Pattern.quote(check.idPrefix() == null ? "" : check.idPrefix()) + "-\\d{3}\\z");
        List<SpecChecklist.Finding> findings = new ArrayList<>();
        for (Row row : table.rows) {
            String rowId = hasId ? row.cells.getOrDefault("ID", "") : "";
            if (hasId) {
                if (rowId.isEmpty()) {
                    findings.add(document.finding("C2", row.line,
                            "'%s' 표의 행에 ID가 없다".formatted(check.heading())));
                } else if (!idPattern.matcher(rowId).matches()) {
                    findings.add(document.finding("C2", row.line,
                            "ID '%s'은 %s-NNN 형식이 아니다".formatted(rowId, check.idPrefix())));
                }
            }
            String name = rowId.isEmpty() ? "ID 없는 행" : rowId;
            for (String column : check.columns()) {
                if ("ID".equals(column)) {
                    continue;
                }
                if (row.cells.getOrDefault(column, "").isBlank()) {
                    findings.add(document.finding("C2", row.line,
                            "%s의 '%s' 칸이 비어 있다".formatted(name, column)));
                }
            }
        }
        return findings;
    }

    /** 라벨이 있고 같은 줄에 내용이 있어야 한다. 빈 라벨은 항목이 없는 것과 같다. */
    private static boolean hasLabelWithContent(List<String> lines, String label) {
        Pattern pattern = Pattern.compile(
                "\\*\\*" + Pattern.quote(label) + "\\s*:\\*\\*\\s*(\\S.*)?$");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher.group(1) != null && !matcher.group(1).isBlank();
            }
        }
        return false;
    }

    private record Section(int line, String name, List<String> body) {
    }

    private static List<Section> sections(List<String> lines, String idPrefix) {
        Pattern heading = Pattern.compile("^##\\s+(" + Pattern.quote(idPrefix) + "-\\d+)\\b");
        List<int[]> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = heading.matcher(lines.get(i));
            if (matcher.find()) {
                starts.add(new int[]{i});
                names.add(matcher.group(1));
            }
        }
        List<Section> sections = new ArrayList<>();
        for (int n = 0; n < starts.size(); n++) {
            int begin = starts.get(n)[0];
            int end = n + 1 < starts.size() ? starts.get(n + 1)[0] : lines.size();
            sections.add(new Section(begin + 1, names.get(n), lines.subList(begin, end)));
        }
        return sections;
    }

    private record Row(int line, Map<String, String> cells) {
    }

    private record MarkdownTable(int headerLine, List<String> columns, List<Row> rows) {

        /** `## 제목` 절의 첫 표. 절이나 표가 없으면 null이다. */
        static MarkdownTable under(List<String> lines, String heading) {
            int start = -1;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).strip();
                if (line.startsWith("## ") && lines.get(i).contains(heading)) {
                    start = i;
                    break;
                }
            }
            if (start < 0) {
                return null;
            }
            List<String> columns = null;
            int headerLine = 0;
            List<Row> rows = new ArrayList<>();
            for (int i = start + 1; i < lines.size(); i++) {
                if (lines.get(i).strip().startsWith("## ")) {
                    break;
                }
                Matcher matcher = TABLE_ROW.matcher(lines.get(i));
                if (!matcher.matches()) {
                    continue;
                }
                List<String> cells = cells(matcher.group(1));
                if (cells.stream().allMatch(MarkdownTable::isRule)) {
                    continue;
                }
                if (columns == null) {
                    columns = cells;
                    headerLine = i + 1;
                    continue;
                }
                Map<String, String> byColumn = new LinkedHashMap<>();
                for (int c = 0; c < Math.min(columns.size(), cells.size()); c++) {
                    byColumn.putIfAbsent(columns.get(c), cells.get(c));
                }
                rows.add(new Row(i + 1, byColumn));
            }
            return columns == null ? null : new MarkdownTable(headerLine, columns, rows);
        }

        private static List<String> cells(String body) {
            List<String> cells = new ArrayList<>();
            for (String cell : body.split("\\|", -1)) {
                cells.add(cell.strip());
            }
            return cells;
        }

        private static boolean isRule(String cell) {
            return cell.chars().allMatch(c -> c == '-' || c == ':' || c == ' ');
        }
    }

    /** 원문에서 뽑은 문서 하나. frontmatter는 검사기와 같은 방식으로 읽는다. */
    private record Parsed(String documentId, String path, List<String> lines, String id, String type) {

        static Parsed of(SourceDocument source) {
            String text = source.markdown() == null ? "" : source.markdown();
            List<String> lines = List.of(text.split("\r?\n", -1));
            String id = null;
            String type = null;
            Matcher matcher = FRONTMATTER.matcher(text);
            if (matcher.find() && matcher.start() == 0) {
                for (String row : matcher.group(1).split("\r?\n")) {
                    int separator = row.indexOf(':');
                    if (separator < 0) {
                        continue;
                    }
                    String key = row.substring(0, separator).strip();
                    String value = row.substring(separator + 1).strip();
                    if ("type".equals(key)) {
                        type = value;
                    } else if ("id".equals(key)) {
                        id = value;
                    }
                }
            }
            return new Parsed(source.documentId(), source.path(), lines, id, type);
        }

        SpecChecklist.Finding finding(String check, String message) {
            return finding(check, 1, message);
        }

        SpecChecklist.Finding finding(String check, int line, String message) {
            return new SpecChecklist.Finding(documentId, path, line, check, message);
        }
    }

    private static SpecChecklist.Finding settingsFinding(int line, String message) {
        return new SpecChecklist.Finding(null, SETTINGS_PATH, line, "C1", message);
    }

    /** 규칙 파일을 읽지 못한 이유를 고르는 데 쓴다. */
    public static Optional<String> uncheckedReason(String format, String settings) {
        if (format == null) {
            return Optional.of(SpecChecklist.DEFINITION_MISSING);
        }
        if (settings == null) {
            return Optional.of(SpecChecklist.APPLY_TABLE_MISSING);
        }
        return Optional.empty();
    }
}
