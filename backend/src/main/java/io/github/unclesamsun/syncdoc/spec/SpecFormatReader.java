package io.github.unclesamsun.syncdoc.spec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * `rules/spec-format.json`을 읽는다.
 *
 * <p>구조가 정의와 어긋나면 **읽지 못한 것으로 본다.** 서비스는 C0(정의 파일 검사)을 하지 않는다.
 * C0은 저장소에서 규칙 표를 고치는 사람을 위한 검사이고, 그 오류를 서비스가 프로젝트의 규약
 * 위반으로 세면 고쳐야 할 곳을 잘못 가리키게 된다. 읽지 못하면 미검사로 알린다.
 */
@Component
public class SpecFormatReader {

    private static final List<String> TYPE_FIELDS =
            List.of("type", "name", "spec", "condition", "required", "labels", "checks");

    private final ObjectMapper json;

    public SpecFormatReader(ObjectMapper json) {
        this.json = json;
    }

    /** @return 읽지 못했으면 비어 있다 */
    public Optional<SpecFormat> read(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (root == null || !root.isObject() || !root.path("types").isArray()) {
            return Optional.empty();
        }

        List<SpecFormat.DocumentType> types = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode entry : root.path("types")) {
            if (!entry.isObject()) {
                return Optional.empty();
            }
            for (String field : TYPE_FIELDS) {
                if (!entry.has(field)) {
                    return Optional.empty();
                }
            }
            String type = entry.path("type").asString();
            if (type == null || type.isBlank() || !seen.add(type)) {
                // 같은 종류가 두 번 있으면 어느 쪽이 정본인지 알 수 없다. 읽지 못한 것으로 본다.
                return Optional.empty();
            }
            List<SpecFormat.Check> checks = new ArrayList<>();
            for (JsonNode check : entry.path("checks")) {
                SpecFormat.Check parsed = checkOf(check);
                if (parsed == null) {
                    return Optional.empty();
                }
                checks.add(parsed);
            }
            types.add(new SpecFormat.DocumentType(type, entry.path("name").asString(),
                    entry.path("spec").asBoolean(), entry.path("condition").asString(),
                    strings(entry.path("required")), strings(entry.path("labels")), checks));
        }
        return Optional.of(SpecFormat.of(types));
    }

    private static SpecFormat.Check checkOf(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        String unit = node.path("unit").asString();
        return switch (unit == null ? "" : unit) {
            case SpecFormat.Check.DOCUMENT -> node.has("labels")
                    ? new SpecFormat.Check(unit, strings(node.path("labels")), null, null, List.of())
                    : null;
            case SpecFormat.Check.SECTION -> node.has("labels") && node.has("idPrefix")
                    ? new SpecFormat.Check(unit, strings(node.path("labels")),
                            node.path("idPrefix").asString(), null, List.of())
                    : null;
            case SpecFormat.Check.TABLE -> node.has("heading") && node.has("columns")
                    && node.has("idPrefix")
                    ? new SpecFormat.Check(unit, List.of(), node.path("idPrefix").asString(),
                            node.path("heading").asString(), strings(node.path("columns")))
                    : null;
            default -> null;
        };
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return List.copyOf(values);
    }
}
