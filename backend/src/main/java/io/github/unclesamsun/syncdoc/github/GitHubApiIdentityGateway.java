package io.github.unclesamsun.syncdoc.github;

import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** GitHub REST API로 사용자 신원을 확인하는 실제 구현. 예외 메시지에 토큰을 담지 않는다. */
public class GitHubApiIdentityGateway implements GitHubIdentityGateway {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private final GitHubProperties properties;
    private final RestClient client;

    public GitHubApiIdentityGateway(GitHubProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.build();
    }

    @Override
    public GitHubUser fetchAuthenticatedUser(String userAccessToken) {
        Map<String, Object> body = get(properties.apiBaseUrl() + "/user", userAccessToken, null);
        return toUser(body);
    }

    @Override
    public GitHubUser fetchUserByLogin(String login) {
        Map<String, Object> body = get(properties.apiBaseUrl() + "/users/" + login, null, login);
        return toUser(body);
    }

    private Map<String, Object> get(String uri, String userAccessToken, String loginForNotFound) {
        try {
            RestClient.RequestHeadersSpec<?> request = client.get()
                    .uri(uri)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28");
            if (userAccessToken != null) {
                request = request.header("Authorization", "Bearer " + userAccessToken);
            }
            Map<String, Object> body = request.retrieve().body(JSON_OBJECT);
            if (body == null) {
                throw new GitHubLookupFailedException("응답이 비어 있다");
            }
            return body;
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 404 && loginForNotFound != null) {
                throw new GitHubUserNotFoundException(loginForNotFound);
            }
            throw new GitHubLookupFailedException("GitHub가 " + status.value() + "로 답했다");
        } catch (RestClientException e) {
            throw new GitHubLookupFailedException("GitHub에 연결하지 못했다");
        }
    }

    /** 숫자 ID를 문자열로 보존한다. 계정명은 바뀔 수 있으므로 동일성 판단에 쓰지 않는다. */
    private static GitHubUser toUser(Map<String, Object> body) {
        Object id = body.get("id");
        Object login = body.get("login");
        if (id == null || login == null) {
            throw new GitHubLookupFailedException("응답에 id 또는 login이 없다");
        }
        String githubUserId = id instanceof Number number ? String.valueOf(number.longValue()) : String.valueOf(id);
        return new GitHubUser(githubUserId, String.valueOf(login));
    }
}
