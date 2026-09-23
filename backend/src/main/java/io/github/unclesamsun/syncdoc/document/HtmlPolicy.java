package io.github.unclesamsun.syncdoc.document;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 변환 결과에서 허용 목록 밖의 것을 지운다.
 *
 * <p>원문을 쓴 사람이 문서에 넣은 HTML이 읽는 사람의 브라우저에서 그대로 실행되면 안 된다.
 * 그렇다고 전부 escape하면 REQ-004가 요구한 접기(`details`)가 죽으므로, 쓸 수 있는 태그와 속성을
 * 정해 두고 나머지를 버린다. 허용하지 않은 속성은 목록에 없다는 이유로 사라지므로
 * `onclick` 같은 이벤트 속성은 따로 열거하지 않아도 남지 않는다.
 *
 * <p>정화는 변환 결과에 적용한다. 정화한 결과를 다시 Markdown으로 해석하지 않는다.
 */
@Component
public class HtmlPolicy {

    /** 정책을 바꾸면 이 값을 올린다. 게시본 식별자에 들어가 이미 만든 게시본이 다시 만들어진다. */
    public static final String VERSION = DocumentVersions.POLICY;

    private final Safelist safelist = buildSafelist();

    /**
     * @param html      정화한 본문
     * @param truncated 중첩이 너무 깊어 끊은 곳이 있으면 true
     */
    public record Sanitized(String html, boolean truncated) {
    }

    public Sanitized sanitize(String html) {
        Document cleaned = Jsoup.parse(Jsoup.clean(html, "", safelist));
        dropForeignImages(cleaned);
        boolean truncated = NestingLimit.apply(cleaned);
        cleaned.outputSettings()
                .prettyPrint(false)
                .escapeMode(Entities.EscapeMode.base)
                .charset("UTF-8");
        return new Sanitized(cleaned.body().html(), truncated);
    }

    /**
     * 서비스가 만든 첨부 주소가 아닌 그림은 버린다.
     *
     * <p>원문에 직접 쓴 `<img src="https://...">`를 그대로 두면 문서를 여는 것만으로 바깥 서버에
     * 요청이 나간다. 누가 어떤 문서를 언제 읽었는지가 그 서버에 남으므로 허용하지 않는다.
     */
    private static void dropForeignImages(Document document) {
        document.select("img").forEach(image -> {
            if (!image.attr("src").startsWith("/api/v1/projects/")) {
                image.remove();
            }
        });
    }

    private static Safelist buildSafelist() {
        Safelist safelist = new Safelist()
                .addTags("p", "br", "hr", "blockquote",
                        "h1", "h2", "h3", "h4", "h5", "h6",
                        "ul", "ol", "li",
                        "pre", "code", "em", "strong", "del", "sup", "sub",
                        "table", "thead", "tbody", "tr", "th", "td",
                        "a", "div", "span", "img",
                        // 명세가 허용한 접기다. 열고 닫는 동작에 script가 필요 없다.
                        "details", "summary")
                // 제목 id는 목차와 문서 간 앵커 이동이 쓴다.
                .addAttributes("h1", "id").addAttributes("h2", "id").addAttributes("h3", "id")
                .addAttributes("h4", "id").addAttributes("h5", "id").addAttributes("h6", "id")
                .addAttributes("a", "href", "title", "id", "data-link-kind")
                .addAttributes("img", "src", "alt", "title")
                .addAttributes("th", "align").addAttributes("td", "align")
                .addAttributes("code", "class")
                .addAttributes("div", "class", "data-diagram-id")
                .addAttributes("span", "class")
                // 다이어그램 자리는 서비스가 만든 표식이다. 원문은 별도 필드로 나간다.
                .addProtocols("a", "href", "http", "https", "mailto")
                .preserveRelativeLinks(true);
        return safelist;
    }
}
