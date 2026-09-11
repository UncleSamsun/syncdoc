package io.github.unclesamsun.syncdoc.document;

/**
 * 게시본의 변환 규칙 버전. 원문이 같아도 이 값이 달라지면 다른 게시본이고 다시 만든다.
 *
 * <p>수집만 있는 지금은 변환기와 정화 정책이 없어 {@code none}이다. 문서 변환을 만드는 TASK-006이
 * 실제 변환기·정화 정책 버전으로 올리면, 이미 수집된 revision도 규칙에 따라 자동으로 다시 만들어진다.
 */
public final class DocumentVersions {

    public static final String RENDERER = "none";
    public static final String POLICY = "none";

    private DocumentVersions() {
    }
}
