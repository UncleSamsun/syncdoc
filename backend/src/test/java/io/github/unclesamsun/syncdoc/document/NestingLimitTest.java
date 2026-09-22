package io.github.unclesamsun.syncdoc.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * 깊게 겹친 블록을 끊는지 본다.
 *
 * <p>이 상한이 없으면 브라우저 탭이 죽는다. 2026-09-21에 실제로 재 본 값은 목록 100단,
 * 인용 200단이었다. 상한은 그보다 훨씬 얕은 50단이다.
 */
class NestingLimitTest {

    @Test
    void ordinary_nesting_is_left_alone() {
        Document document = Jsoup.parse(nested("<ul><li>항목", "</li></ul>", 10) + "");

        assertThat(NestingLimit.apply(document)).isFalse();
        assertThat(document.select("ul")).hasSize(10);
    }

    @Test
    void nesting_deeper_than_the_limit_is_unwrapped() {
        Document document = Jsoup.parse(nested("<ul><li>항목", "</li></ul>", 120));

        assertThat(NestingLimit.apply(document)).isTrue();
        assertThat(deepest(document)).isLessThanOrEqualTo(NestingLimit.MAX_DEPTH);
    }

    @Test
    void the_text_inside_the_cut_is_not_thrown_away() {
        Document document = Jsoup.parse(
                "<blockquote>".repeat(200) + "여기 있던 말" + "</blockquote>".repeat(200));

        assertThat(NestingLimit.apply(document)).isTrue();
        // 겹침만 풀고 내용은 남긴다. 조용히 짧아진 문서는 읽는 사람이 알아챌 수 없다.
        assertThat(document.body().text()).contains("여기 있던 말");
        assertThat(deepest(document)).isLessThanOrEqualTo(NestingLimit.MAX_DEPTH);
    }

    private static String nested(String open, String close, int depth) {
        return open.repeat(depth) + close.repeat(depth);
    }

    /** body 바로 아래를 1로 센 가장 깊은 요소의 깊이. */
    private static int deepest(Document document) {
        int max = 0;
        for (org.jsoup.nodes.Element element : document.body().getAllElements()) {
            if (element == document.body()) {
                continue;
            }
            int depth = 1;
            for (org.jsoup.nodes.Element parent = element.parent();
                    parent != null && parent != document.body(); parent = parent.parent()) {
                depth += 1;
            }
            max = Math.max(max, depth);
        }
        return max;
    }
}
