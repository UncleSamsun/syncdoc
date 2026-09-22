package io.github.unclesamsun.syncdoc.spec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 저장소가 정한 문서 종류 정의. `rules/spec-format.json`을 읽은 결과다.
 *
 * <p>이 파일이 문서 종류·필수 내용·검사 라벨의 기계 정본이다. 검사기(`tools/spec-validator/`)와
 * 서비스가 같은 파일을 읽는다. 어느 한쪽이 규칙을 따로 들고 있지 않게 하려는 것이다.
 */
public record SpecFormat(Map<String, DocumentType> types) {

    public SpecFormat(Map<String, DocumentType> types) {
        this.types = Map.copyOf(types);
    }

    public Optional<DocumentType> type(String name) {
        return Optional.ofNullable(types.get(name));
    }

    /**
     * @param name   사람이 읽는 종류 이름. 화면이 이 값을 그대로 쓴다
     * @param checks 기계 검사 목록. 비어 있으면 C2를 적용하지 않는 종류다
     */
    public record DocumentType(String type, String name, boolean spec, String condition,
                               List<String> required, List<String> labels, List<Check> checks) {
    }

    /**
     * 검사 하나. `unit`이 무엇을 세는지 정한다.
     *
     * @param unit     `document`(문서 전체)·`section`(`## 접두어-NNN` 섹션)·`table`(`## 제목` 절의 첫 표)
     * @param labels   document·section에서 요구하는 굵은 라벨
     * @param idPrefix section의 섹션 ID 접두어, table의 ID 열 접두어
     * @param heading  table이 찾을 `## 제목`
     * @param columns  table에서 채워져야 하는 열
     */
    public record Check(String unit, List<String> labels, String idPrefix, String heading,
                        List<String> columns) {

        public static final String DOCUMENT = "document";
        public static final String SECTION = "section";
        public static final String TABLE = "table";
    }

    /** 정의 파일에 없는 종류도 적용 Spec 표에는 있을 수 있다. 그때는 이름을 종류 값으로 둔다. */
    public String nameOf(String type) {
        DocumentType found = types.get(type);
        return found == null ? type : found.name();
    }

    public static SpecFormat of(List<DocumentType> types) {
        Map<String, DocumentType> byType = new LinkedHashMap<>();
        types.forEach(type -> byType.put(type.type(), type));
        return new SpecFormat(byType);
    }
}
