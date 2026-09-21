package io.github.unclesamsun.syncdoc.document;

import java.util.Locale;
import java.util.Optional;

/**
 * 어떤 첨부를 받아들일지 정한다.
 *
 * <p>첨부는 실행 불가한 데이터로만 다룬다. SVG와 HTML은 그림처럼 보이지만 스크립트를 담을 수 있어
 * 받지 않는다. 확장자만 믿지 않고 앞머리 바이트도 확인해, 이름만 `.png`인 다른 형식을 거른다.
 *
 * <p>정해진 형식만 저장하고 응답의 `Content-Type`도 여기서 정한 값으로만 나간다. 브라우저가
 * 내용을 보고 형식을 새로 추측하지 않도록 응답에 `nosniff`를 함께 보낸다.
 */
public final class AssetPolicy {

    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String WEBP = "image/webp";
    public static final String GIF = "image/gif";

    private AssetPolicy() {
    }

    /** @return 받아들일 수 있는 형식의 MIME. 아니면 비어 있다 */
    public static Optional<String> mimeFor(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return Optional.of(PNG);
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return Optional.of(JPEG);
        }
        if (lower.endsWith(".webp")) {
            return Optional.of(WEBP);
        }
        if (lower.endsWith(".gif")) {
            return Optional.of(GIF);
        }
        return Optional.empty();
    }

    public static boolean isAllowed(String mime) {
        return PNG.equals(mime) || JPEG.equals(mime) || WEBP.equals(mime) || GIF.equals(mime);
    }

    /** 내용의 앞머리가 그 형식이 맞는지 본다. 이름과 내용이 어긋나는 파일은 담지 않는다. */
    public static boolean contentMatches(String mime, byte[] bytes) {
        return switch (mime) {
            case PNG -> startsWith(bytes, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case JPEG -> startsWith(bytes, new int[]{0xFF, 0xD8, 0xFF});
            case GIF -> startsWith(bytes, "GIF87a".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    || startsWith(bytes, "GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            // RIFF....WEBP. 크기 네 바이트는 파일마다 달라 건너뛴다.
            case WEBP -> bytes.length > 12
                    && startsWith(bytes, "RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, int[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
