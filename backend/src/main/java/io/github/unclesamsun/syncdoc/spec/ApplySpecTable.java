package io.github.unclesamsun.syncdoc.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * `rules/project-settings.md`의 적용 Spec 표. 무엇이 있어야 하는지를 이 표가 정한다.
 *
 * <p>표의 형식은 [검증 규칙](../../rules/validation.md) C1이 정한다. 검사기가 읽는 것과 같은 표를
 * 같은 방식으로 읽는다. 표가 없으면 무엇이 있어야 하는지 알 수 없으므로 미검사다.
 */
public record ApplySpecTable(List<Applied> rows) {

    public static final String APPLIED = "적용";
    public static final String DEFERRED = "보류";
    public static final String NOT_APPLIED = "미적용";
    private static final List<String> VALUES = List.of(APPLIED, DEFERRED, NOT_APPLIED);

    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|\\s*$");

    /**
     * @param apply  `적용`·`보류`·`미적용`. 다른 값이면 그 자체가 오류다
     * @param reason 보류·미적용의 사유. 비어 있으면 오류다. 사유 없이 빠뜨리는 것을 막는다
     * @param line   `rules/project-settings.md`의 줄 번호. 오류 위치를 가리킨다
     */
    public record Applied(String type, String apply, String reason, int line) {

        public boolean isKnownValue() {
            return VALUES.contains(apply);
        }

        public boolean needsReason() {
            return DEFERRED.equals(apply) || NOT_APPLIED.equals(apply);
        }
    }

    public ApplySpecTable(List<Applied> rows) {
        this.rows = List.copyOf(rows);
    }

    public Map<String, Applied> byType() {
        Map<String, Applied> map = new LinkedHashMap<>();
        rows.forEach(row -> map.putIfAbsent(row.type(), row));
        return map;
    }

    /** @return 표를 찾지 못했으면 비어 있다 */
    public static Optional<ApplySpecTable> read(String markdown) {
        if (markdown == null) {
            return Optional.empty();
        }
        String[] lines = markdown.split("\r?\n", -1);
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.startsWith("## ") && line.contains("적용 Spec")) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return Optional.empty();
        }

        List<Applied> rows = new ArrayList<>();
        for (int i = start + 1; i < lines.length; i++) {
            if (lines[i].strip().startsWith("## ")) {
                break;
            }
            Matcher matcher = TABLE_ROW.matcher(lines[i]);
            if (!matcher.matches()) {
                continue;
            }
            String[] cells = matcher.group(1).split("\\|", -1);
            if (cells.length < 3) {
                continue;
            }
            String type = cells[0].strip();
            // 머리글 줄과 구분선은 건너뛴다. 표의 내용만 읽는다.
            if (type.isEmpty() || "type".equals(type) || type.chars().allMatch(ApplySpecTable::isRule)) {
                continue;
            }
            rows.add(new Applied(type, cells[1].strip(), cells[2].strip(), i + 1));
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(new ApplySpecTable(rows));
    }

    private static boolean isRule(int codePoint) {
        return codePoint == '-' || codePoint == ':' || codePoint == ' ';
    }
}
