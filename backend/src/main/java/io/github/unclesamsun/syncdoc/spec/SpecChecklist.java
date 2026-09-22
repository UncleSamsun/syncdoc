package io.github.unclesamsun.syncdoc.spec;

import java.util.List;

/**
 * API-025가 돌려주는 산출물 체크리스트. 게시본을 만들 때 그 revision의 규칙 파일로 판정한 결과다.
 *
 * <p>상태 값은 [검증 규칙](../../rules/validation.md) §7의 넷을 그대로 쓴다. 미검사를 통과로 바꾸지
 * 않는다. 오류가 0건이라는 이유로도 바꾸지 않는다. 읽지 못한 것과 지킨 것은 다르다.
 */
public record SpecChecklist(String status, String uncheckedReason, boolean truncated,
                            List<Finding> findings, List<TypeResult> types) {

    /** 통과. 검사 대상이 있고 오류가 없다. */
    public static final String PASS = "pass";
    /** 오류. 하나 이상 있다. */
    public static final String ERROR = "error";
    /** 미작성. `보류`로 정한 종류의 문서가 아직 없다. 오류가 아니다. */
    public static final String PENDING = "pending";
    /** 미검사. 규칙 파일을 읽지 못했다. */
    public static final String UNCHECKED = "unchecked";

    public static final String DEFINITION_MISSING = "DEFINITION_MISSING";
    public static final String APPLY_TABLE_MISSING = "APPLY_TABLE_MISSING";
    public static final String NO_DOCUMENTS = "NO_DOCUMENTS";
    /** 이 게시본은 판정을 넣기 전에 만들어졌다. 다음 수집에서 다시 만들어진다. */
    public static final String NOT_COMPUTED = "NOT_COMPUTED";

    /** 종류 하나의 판정. `미적용` 종류는 검사하지 않았으므로 status가 null이다. */
    public record TypeResult(String type, String name, String apply, String reason, String status,
                             List<DocumentRef> documents, List<Finding> findings) {
    }

    public record DocumentRef(String documentId, String path, String specId) {
    }

    /**
     * 오류 하나.
     *
     * @param documentId 문서에 붙지 않는 오류(없는 문서, 적용 표 자체)면 null
     * @param path       저장소 기준 경로. 적용 표의 오류면 `rules/project-settings.md`다
     * @param line       줄 번호. 문서 전체를 가리키는 오류면 null
     * @param check      `C1`·`C2`
     */
    public record Finding(String documentId, String path, Integer line, String check, String message) {
    }

    public static SpecChecklist unchecked(String reason) {
        return new SpecChecklist(UNCHECKED, reason, false, List.of(), List.of());
    }
}
