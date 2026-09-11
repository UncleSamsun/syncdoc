package io.github.unclesamsun.syncdoc.project;

/**
 * 문서 경로는 저장소 상대 POSIX 경로만 허용한다.
 * 임의 URL과 로컬 경로로 연결할 수 없다는 REQ-002의 인수 기준을 여기서 지킨다.
 */
public final class DocsRootPolicy {

    public static final String DEFAULT = "docs";

    private DocsRootPolicy() {
    }

    /** 앞뒤 슬래시를 떼고 형식을 검사한다. 빈 값은 기본값 `docs`다. */
    public static String normalize(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return DEFAULT;
        }
        String trimmed = candidate.trim();
        if (trimmed.startsWith("/")) {
            throw new InvalidDocsRootException("절대경로는 쓸 수 없다");
        }
        if (trimmed.contains("\\")) {
            throw new InvalidDocsRootException("백슬래시는 쓸 수 없다");
        }
        if (trimmed.contains("://")) {
            throw new InvalidDocsRootException("URL은 쓸 수 없다");
        }
        for (char c : trimmed.toCharArray()) {
            if (Character.isISOControl(c)) {
                throw new InvalidDocsRootException("제어문자는 쓸 수 없다");
            }
        }
        String stripped = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        if (stripped.isEmpty()) {
            return DEFAULT;
        }
        for (String segment : stripped.split("/")) {
            if (segment.isEmpty()) {
                throw new InvalidDocsRootException("빈 경로 조각은 쓸 수 없다");
            }
            if (segment.equals("..") || segment.equals(".")) {
                throw new InvalidDocsRootException("상위 경로 참조는 쓸 수 없다");
            }
        }
        return stripped;
    }

    public static class InvalidDocsRootException extends RuntimeException {

        public InvalidDocsRootException(String reason) {
            super(reason);
        }
    }
}
