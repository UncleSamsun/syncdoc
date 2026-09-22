package io.github.unclesamsun.syncdoc.document;

/**
 * 게시본의 변환 규칙 버전. 원문이 같아도 이 값이 달라지면 다른 게시본이고 다시 만든다.
 *
 * <p>값을 올리면 이미 수집한 revision도 다음 수집에서 새 게시본으로 다시 만들어진다. 변환 결과가
 * 달라지는 변경(변환기 교체·확장 추가·정화 허용 목록 변경)을 할 때마다 해당 값을 올린다.
 */
public final class DocumentVersions {

    /** commonmark-java 0.24.0 + GFM 표 + frontmatter, 제목 앵커·다이어그램 분리·첨부 주소 해소 포함. */
    public static final String RENDERER = "commonmark-0.24.0+2";

    /**
     * 허용 목록 정화 3판. 접기와 서비스 첨부 그림은 허용하고, 바깥 주소 그림과 script는 버린다.
     * 3판에서 블록 중첩 깊이 상한을 더했다.
     */
    public static final String POLICY = "allowlist+3";

    private DocumentVersions() {
    }
}
