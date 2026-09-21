package io.github.unclesamsun.syncdoc.document;

/**
 * 게시본의 변환 규칙 버전. 원문이 같아도 이 값이 달라지면 다른 게시본이고 다시 만든다.
 *
 * <p>값을 올리면 이미 수집한 revision도 다음 수집에서 새 게시본으로 다시 만들어진다. 변환 결과가
 * 달라지는 변경(변환기 교체·확장 추가·정화 허용 목록 변경)을 할 때마다 해당 값을 올린다.
 */
public final class DocumentVersions {

    /** commonmark-java 0.24.0 + GFM 표 + frontmatter, 제목 앵커와 다이어그램 분리 포함. */
    public static final String RENDERER = "commonmark-0.24.0+1";

    /** 허용 목록 정화 1판. 접기는 허용하고 script·이벤트 속성·허용 밖 규약은 버린다. */
    public static final String POLICY = "allowlist+1";

    private DocumentVersions() {
    }
}
