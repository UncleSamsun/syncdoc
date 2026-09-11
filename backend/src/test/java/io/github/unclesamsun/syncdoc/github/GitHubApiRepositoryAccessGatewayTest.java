package io.github.unclesamsun.syncdoc.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubApiRepositoryAccessGatewayTest {

    private MockRestServiceServer server;
    private GitHubApiRepositoryAccessGateway gateway;

    @BeforeEach
    void setUp() {
        GitHubProperties properties = new GitHubProperties("Iv23test", "secret-value", "4895456",
                "http://localhost:5173/api/v1/auth/github/callback",
                "https://github.test", "https://api.github.test");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new GitHubApiRepositoryAccessGateway(properties, builder);
    }

    private void expectInstallations(String json) {
        server.expect(requestTo("https://api.github.test/user/installations?per_page=100&page=1"))
                .andExpect(header("Authorization", "Bearer gho_a"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void expectRepositories(String installationId, int page, String json) {
        server.expect(requestTo("https://api.github.test/user/installations/" + installationId
                        + "/repositories?per_page=100&page=" + page))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void repositories_from_every_installation_are_merged_into_one_list() {
        expectInstallations("{\"installations\":[{\"id\":11},{\"id\":22}]}");
        expectRepositories("11", 1, """
                {"total_count":1,"repositories":[
                  {"id":101,"full_name":"UncleSamsun/syncdoc","private":false,"default_branch":"main"}]}
                """);
        expectRepositories("22", 1, """
                {"total_count":1,"repositories":[
                  {"id":202,"full_name":"acme/secret","private":true,"default_branch":"trunk"}]}
                """);

        RepositoryAccessGateway.AccessibleRepositories result =
                gateway.listAccessibleRepositories("gho_a");

        server.verify();
        assertThat(result.complete()).isTrue();
        assertThat(result.items()).containsExactly(
                new GitHubRepository("101", "UncleSamsun/syncdoc", false, "main", "11"),
                new GitHubRepository("202", "acme/secret", true, "trunk", "22"));
    }

    @Test
    void an_installation_with_no_repositories_contributes_nothing() {
        expectInstallations("{\"installations\":[{\"id\":11}]}");
        expectRepositories("11", 1, "{\"total_count\":0,\"repositories\":[]}");

        assertThat(gateway.listAccessibleRepositories("gho_a").items()).isEmpty();
    }

    /** 상한을 넘으면 잘라 내되 완전한 목록인 척하지 않는다. */
    @Test
    void hitting_the_page_cap_is_reported_as_incomplete() {
        expectInstallations("{\"installations\":[{\"id\":11}]}");
        String fullPage = "{\"total_count\":300,\"repositories\":[" + repositoryJson(1) + "]}";
        for (int page = 1; page <= 3; page++) {
            expectRepositories("11", page, fullPageOf(page));
        }

        RepositoryAccessGateway.AccessibleRepositories result =
                gateway.listAccessibleRepositories("gho_a");

        assertThat(result.complete()).isFalse();
        assertThat(fullPage).isNotEmpty();
    }

    @Test
    void a_repository_is_found_by_its_stable_numeric_id() {
        expectInstallations("{\"installations\":[{\"id\":11}]}");
        expectRepositories("11", 1, """
                {"total_count":1,"repositories":[
                  {"id":101,"full_name":"UncleSamsun/syncdoc","private":false,"default_branch":"main"}]}
                """);

        assertThat(gateway.findRepository("gho_a", "101")).contains(
                new GitHubRepository("101", "UncleSamsun/syncdoc", false, "main", "11"));
    }

    @Test
    void a_repository_the_user_cannot_see_is_simply_absent() {
        expectInstallations("{\"installations\":[{\"id\":11}]}");
        expectRepositories("11", 1, "{\"total_count\":0,\"repositories\":[]}");

        assertThat(gateway.findRepository("gho_a", "999")).isEmpty();
    }

    @Test
    void branches_are_listed_with_the_default_marked() {
        expectInstallations("{\"installations\":[{\"id\":11}]}");
        expectRepositories("11", 1, """
                {"total_count":1,"repositories":[
                  {"id":101,"full_name":"UncleSamsun/syncdoc","private":false,"default_branch":"main"}]}
                """);
        server.expect(requestTo("https://api.github.test/repos/UncleSamsun/syncdoc/branches?per_page=100&page=1"))
                .andRespond(withSuccess("[{\"name\":\"main\"},{\"name\":\"dev\"}]", MediaType.APPLICATION_JSON));

        assertThat(gateway.listBranches("gho_a", "101")).containsExactly(
                new GitHubBranch("main", true),
                new GitHubBranch("dev", false));
    }

    @Test
    void an_existing_path_is_true_and_a_missing_one_is_false() {
        expectInstallations("{\"installations\":[{\"id\":11}]}");
        expectRepositories("11", 1, """
                {"total_count":1,"repositories":[
                  {"id":101,"full_name":"UncleSamsun/syncdoc","private":false,"default_branch":"main"}]}
                """);
        server.expect(requestTo("https://api.github.test/repos/UncleSamsun/syncdoc/contents/docs?ref=main"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(gateway.pathExists("gho_a", "101", "main", "docs")).isTrue();
    }

    @Test
    void a_missing_path_is_false_not_an_error() {
        expectInstallations("{\"installations\":[{\"id\":11}]}");
        expectRepositories("11", 1, """
                {"total_count":1,"repositories":[
                  {"id":101,"full_name":"UncleSamsun/syncdoc","private":false,"default_branch":"main"}]}
                """);
        server.expect(requestTo("https://api.github.test/repos/UncleSamsun/syncdoc/contents/nope?ref=main"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(gateway.pathExists("gho_a", "101", "main", "nope")).isFalse();
    }

    @Test
    void a_revoked_token_fails_without_leaking_the_token() {
        server.expect(ExpectedCount.once(), requestTo(
                        "https://api.github.test/user/installations?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> gateway.listAccessibleRepositories("gho_secret"))
                .isInstanceOf(GitHubLookupFailedException.class)
                .hasMessageNotContaining("gho_secret");
    }

    private static String repositoryJson(int id) {
        return "{\"id\":" + id + ",\"full_name\":\"o/r" + id
                + "\",\"private\":false,\"default_branch\":\"main\"}";
    }

    /** 한 페이지를 가득 채워 다음 페이지가 더 있다고 보이게 만든다. */
    private static String fullPageOf(int page) {
        StringBuilder repositories = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                repositories.append(',');
            }
            repositories.append(repositoryJson(page * 1000 + i));
        }
        return "{\"total_count\":300,\"repositories\":[" + repositories + "]}";
    }
}
