package io.github.unclesamsun.syncdoc.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.auth.CsrfTokenFilter;
import io.github.unclesamsun.syncdoc.auth.SessionService;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.crypto.TokenCipher;
import io.github.unclesamsun.syncdoc.github.FakeRepositoryAccessGateway;
import io.github.unclesamsun.syncdoc.github.FakeRepositoryContentGateway;
import io.github.unclesamsun.syncdoc.github.GitHubRepository;
import io.github.unclesamsun.syncdoc.github.RepositoryAccessGateway;
import io.github.unclesamsun.syncdoc.github.RepositoryContentGateway;
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import io.github.unclesamsun.syncdoc.support.ProjectFixture;
import io.github.unclesamsun.syncdoc.sync.SyncQueue;
import io.github.unclesamsun.syncdoc.sync.SyncWorker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TASK-008. 적대적인 원문을 수집해 화면에 내보내기까지 한 번에 확인한다.
 *
 * <p>정화 규칙 자체는 {@link DocumentRenderingTest}가 단위로 확인한다. 여기서 보는 것은 다른
 * 것이다. 저장소에 실제로 그런 파일이 들어왔을 때 수집이 멈추지 않고, 문서가 조용히 사라지지도
 * 않으며, API가 브라우저에 돌려주는 본문에 실행 가능한 것이 남지 않는지다. 원문은
 * {@code src/test/resources/fixtures/hostile/}에 파일로 둔다. 새 공격 형태를 확인하려면 파일만
 * 늘리면 된다.
 */
@Import(HostileSourceTest.Gateways.class)
class HostileSourceTest extends PostgresContainerSupport {

    /** 브라우저에서 무언가를 실행시킬 수 있는 조각. 응답 어디에도 나오면 안 된다. */
    private static final List<String> FORBIDDEN = List.of(
            "<script", "</script", "<iframe", "<object", "<embed", "<form", "<style", "<base",
            "<link", "<meta", "onerror=", "onload=", "onmouseover=", "onclick=",
            "javascript:", "vbscript:", "data:text/html", "srcdoc=");

    @TestConfiguration
    static class Gateways {

        @Bean
        @Primary
        RepositoryAccessGateway fakeRepositories() {
            return new FakeRepositoryAccessGateway();
        }

        @Bean
        @Primary
        RepositoryContentGateway fakeContents() {
            return new FakeRepositoryContentGateway();
        }
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    UserRepository users;
    @Autowired
    UserCredentialRepository credentials;
    @Autowired
    SessionService sessions;
    @Autowired
    TokenCipher cipher;
    @Autowired
    ProjectFixture fixture;
    @Autowired
    SyncQueue queue;
    @Autowired
    SyncWorker worker;
    @Autowired
    ObjectMapper json;
    @Autowired
    RepositoryAccessGateway accessGateway;
    @Autowired
    RepositoryContentGateway contentGateway;

    @BeforeEach
    void resetGateways() {
        ((FakeRepositoryAccessGateway) accessGateway).reset();
        ((FakeRepositoryContentGateway) contentGateway).reset();
    }

    @Test
    void every_hostile_fixture_is_collected_and_comes_back_without_anything_executable() {
        Map<String, String> fixtures = hostileFixtures();
        assertThat(fixtures).as("공격 fixture가 하나도 없다").isNotEmpty();

        HttpHeaders owner = signIn("9001", "gho_hostile");
        ProjectEntity project = connect("9001", "gho_hostile", "901");
        FakeRepositoryContentGateway contents = (FakeRepositoryContentGateway) contentGateway;
        fixtures.forEach((name, text) -> contents.putFile("rev-1", "docs/" + name, text));

        Instant startedAt = Instant.now();
        queue.request(project.getId(), true);
        assertThat(worker.runOnce()).isTrue();
        Duration collection = Duration.between(startedAt, Instant.now());
        // 깊은 중첩이나 큰 표로 수집이 멎지 않는지 본다. 값은 느슨하게 둔다. 시간을 재는 것이
        // 목적이 아니라 끝나지 않는 경우를 잡는 것이 목적이다.
        assertThat(collection).isLessThan(Duration.ofSeconds(60));

        JsonNode list = json.readTree(
                body(owner, "/api/v1/projects/" + project.getId() + "/documents"));
        // 위험한 원문이라고 문서가 조용히 빠지면 안 된다. 사라진 문서는 화면에서 알 수 없다.
        assertThat(list.path("items").size()).isEqualTo(fixtures.size());

        List<String> checked = new ArrayList<>();
        for (JsonNode item : list.path("items")) {
            ResponseEntity<String> response = get(owner,
                    "/api/v1/projects/" + project.getId() + "/documents/" + item.path("id").asString());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode view = json.readTree(response.getBody());
            String html = view.path("html").asString();
            String path = view.path("path").asString();
            assertThat(view.path("title").asString()).as(path + "의 제목").isNotEmpty();

            String lowered = html.toLowerCase();
            for (String fragment : FORBIDDEN) {
                assertThat(lowered).as(path + "의 본문에 " + fragment).doesNotContain(fragment);
            }
            // 깊게 겹친 블록은 여는 쪽 브라우저 탭을 죽인다. 상한 안에 들어와야 한다.
            assertThat(deepestNesting(html)).as(path + "의 중첩 깊이")
                    .isLessThanOrEqualTo(NestingLimit.MAX_DEPTH);
            checked.add(path);
        }
        assertThat(checked).hasSize(fixtures.size());
    }

    @Test
    void a_document_that_nests_too_deep_says_so_and_keeps_what_was_inside() {
        HttpHeaders owner = signIn("9003", "gho_deep");
        ProjectEntity project = connect("9003", "gho_deep", "903");
        ((FakeRepositoryContentGateway) contentGateway).putFile("rev-1", "docs/deep.md",
                hostileFixtures().get("deep-nesting.md"));

        queue.request(project.getId(), true);
        assertThat(worker.runOnce()).isTrue();

        JsonNode list = json.readTree(
                body(owner, "/api/v1/projects/" + project.getId() + "/documents"));
        JsonNode view = json.readTree(body(owner, "/api/v1/projects/" + project.getId()
                + "/documents/" + list.path("items").get(0).path("id").asString()));

        // 끊었다는 사실을 문서가 스스로 말한다. 조용히 짧아지면 읽는 사람이 알 수 없다.
        List<String> codes = new ArrayList<>();
        view.path("warnings").forEach(warning -> codes.add(warning.path("code").asString()));
        assertThat(codes).contains("CONTENT_TOO_DEEP");
        assertThat(deepestNesting(view.path("html").asString()))
                .isLessThanOrEqualTo(NestingLimit.MAX_DEPTH);
        // 겹침만 풀고 안에 있던 내용은 남긴다.
        assertThat(view.path("html").asString()).contains("깊은 인용");
    }

    @Test
    void a_diagram_comes_back_as_source_the_browser_draws_and_not_as_markup() {
        HttpHeaders owner = signIn("9002", "gho_diagram");
        ProjectEntity project = connect("9002", "gho_diagram", "902");
        ((FakeRepositoryContentGateway) contentGateway).putFile("rev-1", "docs/diagram.md",
                hostileFixtures().get("mermaid-injection.md"));

        queue.request(project.getId(), true);
        assertThat(worker.runOnce()).isTrue();

        JsonNode list = json.readTree(
                body(owner, "/api/v1/projects/" + project.getId() + "/documents"));
        JsonNode view = json.readTree(body(owner, "/api/v1/projects/" + project.getId()
                + "/documents/" + list.path("items").get(0).path("id").asString()));

        // 다이어그램 원문은 본문이 아니라 별도 자리로 나온다. 본문에 그려 넣지 않는다.
        assertThat(view.path("html").asString()).contains("data-diagram-id")
                .doesNotContain("<script");
        assertThat(view.path("diagrams").isEmpty()).isFalse();
        // 원문은 그대로 준다. 화면이 제한된 Mermaid에 넘길 값이라 여기서 지우지 않는다.
        assertThat(view.path("diagrams").get(0).path("source").asString()).contains("flowchart TD");
    }

    /** {@code fixtures/hostile/}의 모든 `.md`를 파일 이름 순서로 읽는다. */
    private static Map<String, String> hostileFixtures() {
        try {
            Path directory = Path.of(
                    HostileSourceTest.class.getResource("/fixtures/hostile").toURI());
            Map<String, String> found = new LinkedHashMap<>();
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(file -> file.getFileName().toString().endsWith(".md"))
                        .sorted()
                        .forEach(file -> found.put(file.getFileName().toString(), readText(file)));
            }
            return found;
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException("공격 fixture를 읽지 못했다", e);
        }
    }

    /** body 바로 아래를 1로 센 가장 깊은 요소의 깊이. */
    private static int deepestNesting(String html) {
        org.jsoup.nodes.Element body = org.jsoup.Jsoup.parseBodyFragment(html).body();
        int max = 0;
        for (org.jsoup.nodes.Element element : body.getAllElements()) {
            if (element == body) {
                continue;
            }
            int depth = 1;
            for (org.jsoup.nodes.Element parent = element.parent();
                    parent != null && parent != body; parent = parent.parent()) {
                depth += 1;
            }
            max = Math.max(max, depth);
        }
        return max;
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private HttpHeaders signIn(String githubUserId, String token) {
        Instant now = Instant.now();
        UserEntity user = users.save(
                new UserEntity(UUID.randomUUID(), githubUserId, "u" + githubUserId, now, now));
        credentials.save(new UserCredentialEntity(user.getId(), cipher.encrypt(token),
                null, null, null, 1));
        String raw = sessions.issue(user.getId()).rawToken();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, SessionService.COOKIE_NAME + "=" + raw);
        headers.add(CsrfTokenFilter.HEADER, sessions.csrfTokenFor(raw));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ProjectEntity connect(String githubUserId, String token, String repositoryId) {
        UUID userId = users.findByGithubUserId(githubUserId).orElseThrow().getId();
        ProjectEntity project = fixture.newProject(repositoryId, userId);
        ((FakeRepositoryAccessGateway) accessGateway).registerRepository(token,
                new GitHubRepository(repositoryId, project.getFullName(), false, "main", "11"));
        return project;
    }

    private ResponseEntity<String> get(HttpHeaders headers, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
    }

    private String body(HttpHeaders headers, String path) {
        ResponseEntity<String> response = get(headers, path);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
