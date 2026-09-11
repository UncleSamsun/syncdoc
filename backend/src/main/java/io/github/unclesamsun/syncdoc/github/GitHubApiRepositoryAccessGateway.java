package io.github.unclesamsun.syncdoc.github;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 저장소 접근의 실제 구현.
 *
 * <p>모든 호출에 **사용자 토큰**을 쓴다. `GET /user/installations/{id}/repositories`가 돌려주는 것이
 * 이미 앱 설치 접근과 사용자 접근의 교집합이므로 서버가 따로 교집합을 계산하지 않는다.
 * 직접 계산하면 GitHub의 판단과 어긋날 수 있고, 설치 토큰으로 읽은 저장소를 사용자에게 보여 줄 위험이 생긴다.
 */
public class GitHubApiRepositoryAccessGateway implements RepositoryAccessGateway {

    /** 계약 `## 공통`의 목록 최대치다. */
    private static final int PER_PAGE = 100;
    /** 설치 하나에서 읽을 최대 페이지. 넘으면 불완전으로 알린다. */
    private static final int MAX_PAGES = 3;

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new ParameterizedTypeReference<>() {
            };

    private final GitHubProperties properties;
    private final RestClient client;

    public GitHubApiRepositoryAccessGateway(GitHubProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.build();
    }

    @Override
    public AccessibleRepositories listAccessibleRepositories(String userAccessToken) {
        List<GitHubRepository> items = new ArrayList<>();
        boolean complete = true;
        for (String installationId : installationIds(userAccessToken)) {
            for (int page = 1; page <= MAX_PAGES; page++) {
                Map<String, Object> body = getObject(
                        properties.apiBaseUrl() + "/user/installations/" + installationId
                                + "/repositories?per_page=" + PER_PAGE + "&page=" + page,
                        userAccessToken);
                List<Map<String, Object>> repositories = listOf(body.get("repositories"));
                repositories.forEach(repository -> items.add(toRepository(repository, installationId)));
                if (repositories.size() < PER_PAGE) {
                    break;
                }
                if (page == MAX_PAGES) {
                    complete = false;
                }
            }
        }
        return new AccessibleRepositories(List.copyOf(items), complete);
    }

    @Override
    public Optional<GitHubRepository> findRepository(String userAccessToken, String githubRepositoryId) {
        return listAccessibleRepositories(userAccessToken).items().stream()
                .filter(repository -> repository.githubRepositoryId().equals(githubRepositoryId))
                .findFirst();
    }

    @Override
    public List<GitHubBranch> listBranches(String userAccessToken, String githubRepositoryId) {
        GitHubRepository repository = require(userAccessToken, githubRepositoryId);
        List<GitHubBranch> branches = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<Map<String, Object>> body = getArray(
                    properties.apiBaseUrl() + "/repos/" + repository.fullName()
                            + "/branches?per_page=" + PER_PAGE + "&page=" + page,
                    userAccessToken);
            body.forEach(branch -> {
                String name = String.valueOf(branch.get("name"));
                branches.add(new GitHubBranch(name, name.equals(repository.defaultBranch())));
            });
            if (body.size() < PER_PAGE) {
                break;
            }
        }
        return List.copyOf(branches);
    }

    @Override
    public boolean pathExists(String userAccessToken, String githubRepositoryId, String branch, String path) {
        GitHubRepository repository = require(userAccessToken, githubRepositoryId);
        try {
            // 디렉터리면 배열, 파일이면 객체가 온다. 어느 쪽이든 200이면 존재한다.
            client.get()
                    .uri(properties.apiBaseUrl() + "/repos/" + repository.fullName()
                            + "/contents/" + path + "?ref=" + branch)
                    .headers(headers -> applyHeaders(headers, userAccessToken))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            throw new GitHubLookupFailedException("GitHub가 " + e.getStatusCode().value() + "로 답했다");
        } catch (RestClientException e) {
            throw new GitHubLookupFailedException("GitHub에 연결하지 못했다");
        }
    }

    private GitHubRepository require(String userAccessToken, String githubRepositoryId) {
        return findRepository(userAccessToken, githubRepositoryId)
                .orElseThrow(() -> new GitHubLookupFailedException("저장소를 볼 수 없다"));
    }

    private List<String> installationIds(String userAccessToken) {
        Map<String, Object> body = getObject(
                properties.apiBaseUrl() + "/user/installations?per_page=" + PER_PAGE + "&page=1",
                userAccessToken);
        return listOf(body.get("installations")).stream()
                .map(installation -> asId(installation.get("id")))
                .toList();
    }

    private static GitHubRepository toRepository(Map<String, Object> body, String installationId) {
        return new GitHubRepository(
                asId(body.get("id")),
                String.valueOf(body.get("full_name")),
                Boolean.TRUE.equals(body.get("private")),
                String.valueOf(body.get("default_branch")),
                installationId);
    }

    /** 숫자 ID를 문자열로 보존한다. 소수점이 붙지 않게 정수로 읽는다. */
    private static String asId(Object value) {
        return value instanceof Number number ? String.valueOf(number.longValue()) : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private Map<String, Object> getObject(String uri, String userAccessToken) {
        Map<String, Object> body = exchange(uri, userAccessToken, JSON_OBJECT);
        return body == null ? Map.of() : body;
    }

    private List<Map<String, Object>> getArray(String uri, String userAccessToken) {
        List<Map<String, Object>> body = exchange(uri, userAccessToken, JSON_ARRAY);
        return body == null ? List.of() : body;
    }

    private <T> T exchange(String uri, String userAccessToken, ParameterizedTypeReference<T> type) {
        try {
            return client.get()
                    .uri(uri)
                    .headers(headers -> applyHeaders(headers, userAccessToken))
                    .retrieve()
                    .body(type);
        } catch (HttpClientErrorException e) {
            throw new GitHubLookupFailedException("GitHub가 " + e.getStatusCode().value() + "로 답했다");
        } catch (RestClientException e) {
            throw new GitHubLookupFailedException("GitHub에 연결하지 못했다");
        }
    }

    private static void applyHeaders(org.springframework.http.HttpHeaders headers, String userAccessToken) {
        headers.set("Authorization", "Bearer " + userAccessToken);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
    }
}
