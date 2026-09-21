package io.github.unclesamsun.syncdoc.document;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 제목에서 앵커 id를 만든다.
 *
 * <p>GitHub가 Markdown 제목에 붙이는 규칙과 같게 만든다. 이 저장소의 문서가 이미
 * `#api-018-문서-본문` 같은 링크로 서로를 가리키고 있어서, 다른 규칙을 쓰면 문서를 화면에서 읽을 때
 * 기존 링크가 전부 깨진다.
 *
 * <p>규칙은 소문자로 바꾸고, 글자·숫자·`_`·`-`만 남기고, 공백을 `-`로 바꾸는 것이다. 한글은 그대로
 * 남는다. 같은 제목이 여러 번 나오면 뒤에 `-1`, `-2`를 붙인다.
 */
public class HeadingSlugs {

    private final Map<String, Integer> used = new HashMap<>();

    /** 같은 문서 안에서 중복되지 않는 id를 돌려준다. */
    public String nextFor(String text) {
        String base = slug(text);
        if (base.isEmpty()) {
            base = "section";
        }
        Integer seen = used.get(base);
        if (seen == null) {
            used.put(base, 0);
            return base;
        }
        int next = seen + 1;
        used.put(base, next);
        return base + "-" + next;
    }

    static String slug(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int index = 0; index < lower.length(); ) {
            int codePoint = lower.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint) || codePoint == '_' || codePoint == '-') {
                out.appendCodePoint(codePoint);
            } else if (codePoint == ' ') {
                out.append('-');
            }
            // 그 밖의 문장부호는 버린다. `·`가 든 제목은 글자만 남아 한 덩어리가 된다.
        }
        return out.toString();
    }
}
