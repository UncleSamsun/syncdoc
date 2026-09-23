package io.github.unclesamsun.syncdoc.document;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 문서 안의 상대 경로를 저장소 기준 경로로 바꾼다.
 *
 * <p>`..`을 따라 저장소 밖으로 나가는 경로는 돌려주지 않는다. 링크 하나로 저장소 밖 파일을
 * 가리키게 두면 첨부·문서 조회가 그 경로를 그대로 쓰게 된다.
 */
public final class DocumentPaths {

    private DocumentPaths() {
    }

    /**
     * @param documentPath 기준이 되는 문서의 저장소 경로
     * @param link         문서에 쓰인 링크 경로
     * @return 저장소 기준 경로. 저장소 밖으로 나가거나 형식이 맞지 않으면 null
     */
    public static String resolve(String documentPath, String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String decoded = decode(link).replace('\\', '/');
        if (decoded.contains("\0") || decoded.startsWith("//")) {
            return null;
        }

        Deque<String> segments = new ArrayDeque<>();
        if (!decoded.startsWith("/")) {
            // 절대 경로가 아니면 문서가 있는 폴더가 기준이다.
            int slash = documentPath.lastIndexOf('/');
            if (slash > 0) {
                for (String segment : documentPath.substring(0, slash).split("/")) {
                    if (!segment.isEmpty()) {
                        segments.addLast(segment);
                    }
                }
            }
        }

        for (String segment : decoded.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    return null;
                }
                segments.removeLast();
                continue;
            }
            segments.addLast(segment);
        }
        return segments.isEmpty() ? null : String.join("/", segments);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
