package io.github.unclesamsun.syncdoc.document;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 겹쳐 쓴 블록의 깊이를 끊는다.
 *
 * <p>목록이나 인용을 깊게 겹친 원문은 변환·게시까지 아무 문제 없이 지나가지만, 그 문서를 여는
 * 브라우저 탭이 죽는다. 2026-09-21에 잰 값은 목록 100단, 인용 200단이다. 저장소에 그런 파일이
 * 하나 있으면 읽는 사람이 화면을 잃는다.
 *
 * <p>그래서 정한 깊이를 넘는 요소는 껍데기만 벗긴다. 안에 있던 내용은 버리지 않고 끊긴 자리에
 * 그대로 이어 붙는다. 내용을 지우면 문서가 조용히 짧아지고, 그건 읽는 사람이 알아챌 수 없다.
 */
public final class NestingLimit {

    /**
     * 본문 기준 허용 깊이. 실제 문서에서 쓰는 겹침보다 훨씬 깊고, 탭이 죽는 깊이보다는 훨씬 얕다.
     */
    public static final int MAX_DEPTH = 50;

    private NestingLimit() {
    }

    /**
     * @return 끊은 곳이 있으면 true
     */
    public static boolean apply(Document document) {
        Element body = document.body();
        List<Element> tooDeep = new ArrayList<>();
        // 먼저 전부 재고 나서 벗긴다. 벗기는 도중에 재면 깊이가 계속 바뀐다.
        for (Element element : body.getAllElements()) {
            if (element != body && depthUnder(body, element) > MAX_DEPTH) {
                tooDeep.add(element);
            }
        }
        // 얕은 것부터 벗긴다. 안쪽 것들은 한 칸씩 올라오지만 어차피 모두 벗길 대상이라,
        // 끝나고 나면 살아남은 요소는 모두 허용 깊이 안에 있다.
        tooDeep.forEach(Element::unwrap);
        return !tooDeep.isEmpty();
    }

    /** body 바로 아래를 1로 센다. */
    private static int depthUnder(Element body, Element element) {
        int depth = 1;
        for (Element parent = element.parent(); parent != null && parent != body; parent = parent.parent()) {
            depth += 1;
        }
        return depth;
    }
}
