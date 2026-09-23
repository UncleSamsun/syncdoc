package io.github.unclesamsun.syncdoc.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 판정기가 저장소의 검사기와 같은 판정을 내는지 본다.
 *
 * <p>입력은 검사기의 fixture를 그대로 쓴다. 각 fixture가 작은 저장소 트리(`docs/` + `rules/`)라
 * 따로 만들 필요가 없고, 무엇보다 **두 구현이 같은 입력을 보고 갈리는지**가 검사 대상이다.
 * 기대값은 `python tools/spec-validator/validate.py tools/spec-validator/fixtures/<이름>`의 출력을
 * 그대로 옮긴 것이다.
 *
 * <p>C0만 다르다. 서비스는 C0을 하지 않으므로 정의 파일 자체가 깨진 fixture에서 검사기는 오류를
 * 내지만 서비스는 미검사다. 의도한 차이이며 아래 시험이 그 차이를 고정한다.
 */
class ChecklistCheckerTest {

    private static final Path FIXTURES = Path.of("..", "tools", "spec-validator", "fixtures");

    private final ChecklistChecker checker = new ChecklistChecker();
    private final SpecFormatReader formats = new SpecFormatReader(new ObjectMapper());

    @Test
    void a_repository_that_follows_the_rules_has_no_findings() {
        SpecChecklist checklist = check("ok");

        assertThat(flatten(checklist)).isEmpty();
        // `보류`로 둔 종류의 문서가 아직 없다. 오류가 아니라 미작성이다.
        assertThat(checklist.status()).isEqualTo(SpecChecklist.PENDING);
        assertThat(pendingTypes(checklist)).containsExactly("tech-ops");
    }

    @Test
    void the_c1_fixture_gives_the_same_findings_as_the_validator() {
        assertThat(flatten(check("bad-c1"))).containsExactlyInAnyOrder(
                "docs/badid.md:1 [C1] id 'DOC-1'은 DOC-NNN 형식이 아니다",
                "docs/dupb.md:1 [C1] id 'DOC-100'이 docs/dupa.md와 중복된다",
                "docs/nofm.md:1 [C1] frontmatter의 id가 없어 문서를 참조할 수 없다",
                "docs/noid.md:1 [C1] frontmatter의 id가 없어 문서를 참조할 수 없다",
                "docs/nofm.md:1 [C1] frontmatter의 type이 없어 문서를 분류할 수 없다",
                "docs/oldtype.md:1 [C1] type 'note'은 허용 값이 아니다",
                "docs/weird.md:1 [C1] type 'tech-overview'이 적용 Spec 표에 없다",
                "rules/project-settings.md:9 [C1] 'tech-ops'을 보류로 두었으나 사유가 없다",
                "rules/project-settings.md:8 [C1] 'ui-screens' 문서가 없다",
                "docs/req.md:9 [C2] REQ-001에 '**예외:**' 항목이 없다",
                "docs/req.md:9 [C2] REQ-001에 '**근거:**' 항목이 없다");
    }

    @Test
    void the_c2_fixture_gives_the_same_findings_as_the_validator() {
        assertThat(flatten(check("bad-c2"))).containsExactlyInAnyOrder(
                "docs/api-notable.md:1 [C2] '## 계약 일람' 표가 없어 행을 셀 수 없다",
                "docs/api.md:13 [C2] API-001의 '연결 요구' 칸이 비어 있다",
                "docs/api.md:14 [C2] ID 'API-2'은 API-NNN 형식이 아니다",
                "docs/api.md:1 [C2] '**접근 조건:**' 항목이 없다",
                "docs/api.md:1 [C2] '**부작용:**' 항목이 없다",
                "docs/api.md:1 [C2] '**재시도:**' 항목이 없다",
                "docs/overview.md:1 [C2] '**성공 판단:**' 항목이 없다",
                "docs/overview.md:1 [C2] '**제약:**' 항목이 없다",
                "docs/overview.md:1 [C2] '**용어:**' 항목이 없다",
                "docs/req.md:9 [C2] REQ-001에 '**예외:**' 항목이 없다",
                "docs/req.md:9 [C2] REQ-001에 '**인수 기준:**' 항목이 없다",
                "docs/req.md:9 [C2] REQ-001에 '**근거:**' 항목이 없다",
                "docs/screens.md:9 [C2] UI-001에 '**연결 요구:**' 항목이 없다",
                "docs/screens.md:9 [C2] UI-001에 '**검증:**' 항목이 없다",
                "docs/task2.md:9 [C2] TASK-001에 '**선행:**' 항목이 없다",
                "docs/task2.md:9 [C2] TASK-001에 '**산출물:**' 항목이 없다",
                "docs/task2.md:9 [C2] TASK-001에 '**검증:**' 항목이 없다",
                "docs/tasks.md:1 [C2] 검사 단위 '## TASK-NNN' 섹션이 하나도 없다");
    }

    @Test
    void a_repository_without_the_definition_file_is_unchecked() {
        SpecChecklist checklist = check("no-format");

        assertThat(checklist.status()).isEqualTo(SpecChecklist.UNCHECKED);
        assertThat(checklist.uncheckedReason()).isEqualTo(SpecChecklist.DEFINITION_MISSING);
        // 오류 0건이라고 통과로 바꾸지 않는다.
        assertThat(checklist.status()).isNotEqualTo(SpecChecklist.PASS);
    }

    @Test
    void a_repository_without_the_apply_table_is_unchecked() {
        SpecChecklist checklist = check("no-settings");

        assertThat(checklist.status()).isEqualTo(SpecChecklist.UNCHECKED);
        assertThat(checklist.uncheckedReason()).isEqualTo(SpecChecklist.APPLY_TABLE_MISSING);
    }

    @Test
    void a_broken_definition_file_is_unchecked_because_the_service_does_not_run_c0() {
        // 검사기는 이 셋을 C0 오류로 낸다. 서비스는 C0을 하지 않으므로 정의 파일을 읽지 못한 것으로
        // 본다. 그 오류를 프로젝트의 규약 위반으로 세면 고쳐야 할 곳을 잘못 가리킨다.
        for (String fixture : List.of("bad-format-syntax", "bad-format-unit", "bad-format-duplicate")) {
            SpecChecklist checklist = check(fixture);
            assertThat(checklist.status()).as(fixture).isEqualTo(SpecChecklist.UNCHECKED);
            assertThat(checklist.uncheckedReason()).as(fixture)
                    .isEqualTo(SpecChecklist.DEFINITION_MISSING);
        }
    }

    @Test
    void the_types_carry_what_the_apply_table_said_and_which_documents_belong_to_them() {
        SpecChecklist checklist = check("ok");

        SpecChecklist.TypeResult deferred = typeOf(checklist, "tech-ops");
        assertThat(deferred.apply()).isEqualTo("보류");
        assertThat(deferred.reason()).isNotEmpty();
        assertThat(deferred.status()).isEqualTo(SpecChecklist.PENDING);
        assertThat(deferred.documents()).isEmpty();

        SpecChecklist.TypeResult applied = typeOf(checklist, "prd-requirements");
        assertThat(applied.status()).isEqualTo(SpecChecklist.PASS);
        assertThat(applied.documents()).isNotEmpty();
        assertThat(applied.documents().get(0).specId()).startsWith("DOC-");
    }

    @Test
    void findings_are_cut_at_the_limit_and_the_result_says_so() {
        SpecFormat format = SpecFormat.of(List.of(new SpecFormat.DocumentType(
                "guide", "안내", true, "언제나",
                List.of(), List.of(),
                List.of(new SpecFormat.Check(SpecFormat.Check.DOCUMENT, labels(80), null, null,
                        List.of())))));
        ApplySpecTable table = ApplySpecTable.read("""
                ## 적용 Spec

                | type | 적용 | 사유 |
                |---|---|---|
                | guide | 적용 | |
                """).orElseThrow();
        List<ChecklistChecker.SourceDocument> documents = List.of(new ChecklistChecker.SourceDocument(
                UUID.randomUUID().toString(), "docs/guide.md", """
                        ---
                        id: DOC-001
                        type: guide
                        ---

                        # 안내
                        """));

        SpecChecklist checklist = checker.check(format, table, documents);

        assertThat(checklist.truncated()).isTrue();
        assertThat(typeOf(checklist, "guide").findings())
                .hasSize(ChecklistChecker.MAX_FINDINGS_PER_TYPE);
    }

    private static List<String> labels(int count) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            labels.add("라벨" + i);
        }
        return labels;
    }

    private SpecChecklist check(String fixture) {
        Path root = FIXTURES.resolve(fixture);
        SpecFormat format = read(root.resolve("rules/spec-format.json"))
                .flatMap(formats::read)
                .orElse(null);
        ApplySpecTable table = read(root.resolve("rules/project-settings.md"))
                .flatMap(ApplySpecTable::read)
                .orElse(null);
        return checker.check(format, table, documentsUnder(root));
    }

    private static List<ChecklistChecker.SourceDocument> documentsUnder(Path root) {
        Path docs = root.resolve("docs");
        if (!Files.isDirectory(docs)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(docs)) {
            return files.filter(path -> path.toString().endsWith(".md"))
                    .sorted()
                    .map(path -> new ChecklistChecker.SourceDocument(UUID.randomUUID().toString(),
                            root.relativize(path).toString().replace('\\', '/'),
                            read(path).orElse("")))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<String> read(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 검사기 출력과 같은 모양으로 편다. `파일:줄 [검사] 내용`이다. */
    private static List<String> flatten(SpecChecklist checklist) {
        List<SpecChecklist.Finding> all = new ArrayList<>(checklist.findings());
        checklist.types().forEach(type -> all.addAll(type.findings()));
        return all.stream()
                .map(finding -> "%s:%d [%s] %s".formatted(finding.path(),
                        finding.line() == null ? 1 : finding.line(), finding.check(), finding.message()))
                .toList();
    }

    private static List<String> pendingTypes(SpecChecklist checklist) {
        return checklist.types().stream()
                .filter(type -> SpecChecklist.PENDING.equals(type.status()))
                .map(SpecChecklist.TypeResult::type)
                .toList();
    }

    private static SpecChecklist.TypeResult typeOf(SpecChecklist checklist, String type) {
        return checklist.types().stream()
                .filter(result -> result.type().equals(type))
                .findFirst()
                .orElseThrow(() -> new AssertionError("종류 " + type + "가 결과에 없다"));
    }
}
