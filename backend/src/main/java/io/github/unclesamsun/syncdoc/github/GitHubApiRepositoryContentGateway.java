package io.github.unclesamsun.syncdoc.github;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 원문 읽기의 실제 구현.
 *
 * <p>저장소는 숫자 ID로 먼저 해소한다. 이름이 바뀌어도 같은 프로젝트를 계속 따라가기 위해서다.
 *
 * <p>문서 목록은 저장소 전체 트리 대신 문서 경로 아래만 훑는다. 큰 저장소에서 전체 트리를 받으면
 * GitHub가 결과를 잘라 보내는데, 잘린 목록을 그대로 쓰면 문서가 사라진 것처럼 보인다.
 */
public class GitHubApiRepositoryContentGateway implements RepositoryContentGateway {

    private static final int PER_PAGE = 100;
    /** 문서 경로 아래로 내려갈 최대 깊이. 순환·과도한 중첩에서 멈춘다. */
    private static final int MAX_DEPTH = 10;

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new ParameterizedTypeReference<>() {
            };

    private final GitHubProperties properties;
    private final InstallationTokenGateway tokens;
    private final RestClient client;

    public GitHubApiRepositoryContentGateway(GitHubProperties properties, InstallationTokenGateway tokens,
                                             RestClient.Builder builder) {
        this.properties = properties;
        this.tokens = tokens;
        this.client = builder.build();
    }

    @Override
    public RepositoryRef open(String githubInstallationId, String githubRepositoryId) {
        String token = tokens.accessToken(githubInstallationId);
        Map<String, Object> repository = getObject(
                properties.apiBaseUrl() + "/repositories/" + githubRepositoryId, token);
        Object fullName = repository.get("full_name");
        if (fullName == null) {
            throw new GitHubLookupFailedException("저장소를 확인할 수 없다");
        }
        return new RepositoryRef(githubInstallationId, githubRepositoryId, String.valueOf(fullName));
    }

    @Override
    public String headRevision(RepositoryRef repository, String branch) {
        String token = tokens.accessToken(repository.githubInstallationId());
        Map<String, Object> commit = getObject(
                properties.apiBaseUrl() + "/repos/" + repository.fullName() + "/commits/" + branch, token);
        Object sha = commit.get("sha");
        if (sha == null) {
            throw new GitHubLookupFailedException("브랜치 " + branch + "의 commit을 확인할 수 없다");
        }
        return String.valueOf(sha);
    }

    @Override
    public List<SourceFile> listDocuments(RepositoryRef repository, String revision, String docsRoot,
                                          int maxDocuments) {
        String token = tokens.accessToken(repository.githubInstallationId());
        String fullName = repository.fullName();
        List<SourceFile> files = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(docsRoot);
        boolean root = true;

        while (!pending.isEmpty()) {
            String directory = pending.removeFirst();
            if (depthOf(directory) - depthOf(docsRoot) > MAX_DEPTH) {
                continue;
            }
            List<Map<String, Object>> entries = contents(fullName, directory, revision, token, root);
            root = false;
            for (Map<String, Object> entry : entries) {
                String type = String.valueOf(entry.get("type"));
                String path = String.valueOf(entry.get("path"));
                if ("dir".equals(type)) {
                    pending.addLast(path);
                } else if ("file".equals(type) && path.endsWith(".md")) {
                    if (files.size() >= maxDocuments) {
                        throw new TooManyDocumentsException(maxDocuments);
                    }
                    files.add(new SourceFile(path, String.valueOf(entry.get("sha")), sizeOf(entry.get("size"))));
                }
                // symlink와 submodule은 담지 않는다. 저장소 밖을 가리킬 수 있다.
            }
        }
        return List.copyOf(files);
    }

    @Override
    public String readText(RepositoryRef repository, String blobSha, int maxBytes) {
        String token = tokens.accessToken(repository.githubInstallationId());
        Map<String, Object> blob = getObject(
                properties.apiBaseUrl() + "/repos/" + repository.fullName() + "/git/blobs/" + blobSha, token);
        if (!"base64".equals(String.valueOf(blob.get("encoding")))) {
            throw new GitHubLookupFailedException("예상하지 못한 blob 인코딩이다");
        }
        byte[] decoded = Base64.getMimeDecoder().decode(String.valueOf(blob.get("content")));
        if (decoded.length > maxBytes) {
            throw new DocumentTooLargeException(blobSha, maxBytes);
        }
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /**
     * @param root true면 404를 문서 경로 없음으로 본다. 하위 디렉터리의 404는 수집 도중
     *             저장소가 바뀐 경우이므로 조회 실패로 다룬다
     */
    private List<Map<String, Object>> contents(String fullName, String path, String revision, String token,
                                               boolean root) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (int page = 1; ; page++) {
            try {
                List<Map<String, Object>> body = client.get()
                        .uri(properties.apiBaseUrl() + "/repos/" + fullName + "/contents/" + path
                                + "?ref=" + revision + "&per_page=" + PER_PAGE + "&page=" + page)
                        .headers(headers -> applyHeaders(headers, token))
                        .retrieve()
                        .body(JSON_ARRAY);
                List<Map<String, Object>> entries = body == null ? List.of() : body;
                all.addAll(entries);
                if (entries.size() < PER_PAGE) {
                    return List.copyOf(all);
                }
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404 && root) {
                    throw new DocsRootMissingException(path);
                }
                throw translate(e);
            } catch (RestClientException e) {
                throw new GitHubLookupFailedException("GitHub에 연결하지 못했다");
            }
        }
    }

    private Map<String, Object> getObject(String uri, String token) {
        try {
            Map<String, Object> body = client.get()
                    .uri(uri)
                    .headers(headers -> applyHeaders(headers, token))
                    .retrieve()
                    .body(JSON_OBJECT);
            return body == null ? Map.of() : body;
        } catch (HttpClientErrorException e) {
            throw translate(e);
        } catch (RestClientException e) {
            throw new GitHubLookupFailedException("GitHub에 연결하지 못했다");
        }
    }

    /** 429와 제한 소진 403은 재시도 시각이 따로 있다. 나머지는 상태 코드만 남긴다. */
    private static RuntimeException translate(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        if (status == 429 || (status == 403 && "0".equals(e.getResponseHeaders() == null
                ? null : e.getResponseHeaders().getFirst("x-ratelimit-remaining")))) {
            return new GitHubRateLimitedException(retryAfter(e.getResponseHeaders()));
        }
        return new GitHubLookupFailedException("GitHub가 " + status + "로 답했다");
    }

    private static Instant retryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String retryAfter = headers.getFirst("retry-after");
        if (retryAfter != null) {
            try {
                return Instant.now().plusSeconds(Long.parseLong(retryAfter.trim()));
            } catch (NumberFormatException ignored) {
                // 날짜 형식일 수 있다. 아래 reset 헤더로 넘어간다.
            }
        }
        String reset = headers.getFirst("x-ratelimit-reset");
        if (reset != null) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(reset.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int sizeOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int depthOf(String path) {
        return (int) path.chars().filter(character -> character == '/').count();
    }

    private static void applyHeaders(HttpHeaders headers, String token) {
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
    }
}
