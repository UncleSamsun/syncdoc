package io.github.unclesamsun.syncdoc.document;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.document.domain.DocumentEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotEntity;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * API-019 검색.
 *
 * <p>대상은 **현재 프로젝트의 한 게시본**뿐이다. 다른 프로젝트의 문서는 질의에 들어오지 않는다.
 * 프로젝트를 볼 수 있는지는 문서 조회와 같은 방법으로 먼저 확인한다.
 *
 * <p>저장한 본문에서 그대로 찾는다(2026-09-21 사용자 확정). 한국어는 PostgreSQL 기본 전문검색
 * 파서가 어절을 제대로 나누지 못해, 형태소 분석기를 달지 않는 한 색인을 써도 체감이 나빠질 수 있다.
 * 문서 수가 많아지면 색인을 다시 본다.
 */
@Service
public class SearchService {

    /** 계약이 정한 질의 길이. 넘으면 조용히 자르지 않고 알린다. */
    public static final int MAX_QUERY_LENGTH = 200;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    /** 발췌에서 찾은 말 앞뒤로 보여줄 글자 수. */
    private static final int CONTEXT = 60;

    private final ProjectService projects;
    private final DocumentService documents;
    private final DocumentRepository documentRepository;
    private final ObjectMapper json;

    public SearchService(ProjectService projects, DocumentService documents,
                         DocumentRepository documentRepository, ObjectMapper json) {
        this.projects = projects;
        this.documents = documents;
        this.documentRepository = documentRepository;
        this.json = json;
    }

    /**
     * @param anchor  찾은 자리 바로 앞 제목의 앵커. 문서 첫머리에서 찾았으면 null
     * @param excerpt 앞뒤를 잘라낸 본문 조각. HTML이 아니라 글자만 담는다
     */
    public record SearchHit(UUID documentId, String path, String title, String anchor, String excerpt) {
    }

    public record SearchResults(UUID snapshotId, String query, List<SearchHit> items, int total) {
    }

    @Transactional(readOnly = true)
    public SearchResults search(CurrentUser user, UUID projectId, String query, UUID requestedSnapshotId,
                                Integer limit) {
        if (query == null || query.isBlank()) {
            throw new InvalidQueryException("검색어를 입력하세요.");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new InvalidQueryException("검색어는 " + MAX_QUERY_LENGTH + "자까지입니다.");
        }

        ProjectService.ProjectView project = projects.view(user, projectId);
        DocumentSnapshotEntity snapshot = documents.snapshotFor(project, requestedSnapshotId).orElse(null);
        if (snapshot == null) {
            // 첫 수집 전이다. 결과 0건과 구분해 게시본이 없다는 사실을 그대로 전한다.
            return new SearchResults(null, query, List.of(), 0);
        }

        String needle = query.trim().toLowerCase(Locale.ROOT);
        int max = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        List<SearchHit> hits = new ArrayList<>();
        int total = 0;

        for (DocumentEntity document : documentRepository.findBySnapshotIdOrderByPath(snapshot.getId())) {
            int at = indexOf(document, needle);
            if (at < 0) {
                continue;
            }
            total++;
            if (hits.size() < max) {
                hits.add(new SearchHit(document.getId(), document.getPath(), document.getTitle(),
                        anchorAt(document, at), excerpt(document.getPlainText(), at, needle.length())));
            }
        }
        return new SearchResults(snapshot.getId(), query, List.copyOf(hits), total);
    }

    private static int indexOf(DocumentEntity document, String needle) {
        int inText = document.getPlainText().toLowerCase(Locale.ROOT).indexOf(needle);
        if (inText >= 0) {
            return inText;
        }
        // 제목에만 있는 말도 찾는다. 이때 발췌는 본문 첫머리를 쓴다.
        return document.getTitle().toLowerCase(Locale.ROOT).contains(needle) ? 0 : -1;
    }

    /** 찾은 자리 앞에 나온 마지막 제목의 앵커. 결과를 고르면 그 자리로 이동할 수 있게 한다. */
    private String anchorAt(DocumentEntity document, int at) {
        try {
            JsonNode headings = json.readTree(document.getHeadingsJson());
            String plain = document.getPlainText();
            String anchor = null;
            for (JsonNode heading : headings) {
                String text = heading.path("text").asString();
                if (text == null || text.isBlank()) {
                    continue;
                }
                int position = plain.indexOf(text);
                if (position >= 0 && position <= at) {
                    anchor = heading.path("id").asString();
                }
            }
            return anchor;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String excerpt(String text, int at, int length) {
        int from = Math.max(0, at - CONTEXT);
        int to = Math.min(text.length(), at + length + CONTEXT);
        String body = text.substring(from, to).trim();
        return (from > 0 ? "… " : "") + body + (to < text.length() ? " …" : "");
    }

    public static class InvalidQueryException extends RuntimeException {

        public InvalidQueryException(String message) {
            super(message);
        }
    }
}
