package io.github.unclesamsun.syncdoc.document;

import java.util.List;
import java.util.Map;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.springframework.stereotype.Component;

/**
 * 문서 앞머리(frontmatter)의 규약 메타데이터를 읽는다.
 *
 * <p>규칙 정본은 대상 저장소의 `rules/`에 있고 SyncDoc은 그것을 읽어 준수 여부만 표시한다.
 * 이 파서는 검사기(`tools/spec-validator/`)와 같은 필드를 읽으며, 두 구현이 서로를 베끼지 않고
 * 규칙 파일을 각자 따른다.
 *
 * <p>값이 없거나 형식이 다르면 오류로 만들지 않고 비워 둔다. 규약을 따르지 않는 문서도
 * 읽을 수는 있어야 하고, 준수 여부 표시는 산출물 체크리스트가 할 일이다.
 */
@Component
public class SpecMetadataParser {

    /**
     * @param specId 문서 ID. `DOC-014` 같은 값이며 같은 게시본 안에서 유일해야 한다
     * @param kind   문서 종류. 규칙 파일이 정의한 `tasks`·`tech-interface` 같은 값이다
     * @param status 작성 상태. 지금은 보관만 한다
     */
    public record SpecMetadata(String specId, String kind, String status) {

        public static final SpecMetadata EMPTY = new SpecMetadata(null, null, null);
    }

    public SpecMetadata parse(Node document) {
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        Map<String, List<String>> data = visitor.getData();
        if (data.isEmpty()) {
            return SpecMetadata.EMPTY;
        }
        return new SpecMetadata(first(data, "id"), first(data, "type"), first(data, "status"));
    }

    private static String first(Map<String, List<String>> data, String key) {
        List<String> values = data.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String value = values.getFirst();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
