package io.github.unclesamsun.syncdoc.github;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Project(v2) 조회의 실제 구현. REST에는 Project(v2)가 없어 GraphQL을 쓴다.
 *
 * <p>조회할 수 없으면 예외를 밖으로 던지지 않고 비어 있는 결과를 돌려준다. Project를 못 본다고
 * 문서·Issue 현황까지 막히면 안 되기 때문이다. 판단은 부르는 쪽이 한다.
 */
public class GitHubGraphQlProjectBoardGateway implements ProjectBoardGateway {

    private static final Logger log = LoggerFactory.getLogger(GitHubGraphQlProjectBoardGateway.class);

    /** 한 번에 읽을 항목 수. 더 있으면 전부 읽지 못한 것이므로 집계를 확정으로 쓰지 않는다. */
    private static final int PAGE_SIZE = 100;

    private static final String QUERY = """
            query($id: ID!, $first: Int!) {
              node(id: $id) {
                ... on ProjectV2 {
                  items(first: $first) {
                    totalCount
                    nodes {
                      fieldValueByName(name: "Status") {
                        ... on ProjectV2ItemFieldSingleSelectValue { name }
                      }
                    }
                  }
                }
              }
            }
            """;

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private final GitHubProperties properties;
    private final RestClient client;

    public GitHubGraphQlProjectBoardGateway(GitHubProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.build();
    }

    @Override
    public Optional<BoardCounts> countByStatus(String userAccessToken, String githubProjectNodeId) {
        if (githubProjectNodeId == null || githubProjectNodeId.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> body = client.post()
                    .uri(properties.apiBaseUrl() + "/graphql")
                    .headers(headers -> {
                        headers.set("Authorization", "Bearer " + userAccessToken);
                        headers.set("Accept", "application/vnd.github+json");
                    })
                    .body(Map.of("query", QUERY,
                            "variables", Map.of("id", githubProjectNodeId, "first", PAGE_SIZE)))
                    .retrieve()
                    .body(JSON_OBJECT);
            return Optional.ofNullable(parse(body));
        } catch (RestClientException e) {
            // 권한이 없거나 GraphQL이 거절했다. 0으로 대체하지 않고 조회 불가로 둔다.
            log.debug("Project를 조회하지 못했다");
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static BoardCounts parse(Map<String, Object> body) {
        if (body == null || body.get("errors") != null) {
            return null;
        }
        Object data = body.get("data");
        if (!(data instanceof Map<?, ?> dataMap) || !(dataMap.get("node") instanceof Map<?, ?> node)) {
            return null;
        }
        if (!(node.get("items") instanceof Map<?, ?> items)) {
            return null;
        }
        Map<String, Integer> counts = new HashMap<>();
        Object nodes = items.get("nodes");
        if (nodes instanceof List<?> list) {
            for (Object element : list) {
                String status = statusOf(element);
                counts.merge(status, 1, Integer::sum);
            }
        }
        int total = items.get("totalCount") instanceof Number number ? number.intValue() : counts.size();
        return new BoardCounts(Map.copyOf(counts), total);
    }

    private static String statusOf(Object item) {
        if (item instanceof Map<?, ?> map && map.get("fieldValueByName") instanceof Map<?, ?> field
                && field.get("name") != null) {
            return String.valueOf(field.get("name"));
        }
        // Status를 비워 둔 항목이다. 임의의 상태로 옮기지 않고 그대로 센다.
        return "(없음)";
    }
}
