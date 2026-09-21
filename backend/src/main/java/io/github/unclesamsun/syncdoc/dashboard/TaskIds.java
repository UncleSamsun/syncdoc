package io.github.unclesamsun.syncdoc.dashboard;

import io.github.unclesamsun.syncdoc.document.MarkdownRenderService.DocumentHeading;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 작업 ID를 읽는 규칙 한 곳.
 *
 * <p>작업계획 문서의 제목에서 작업을 뽑고, Issue 제목의 접두사로 작업과 Issue를 잇는다
 * (2026-09-21 사용자 확정). 같은 규칙을 여러 곳에 흩어 두면 한쪽만 고쳐져 연결이 조용히 끊긴다.
 */
public final class TaskIds {

    /** 작업계획 문서의 `## TASK-005 수집·스냅샷·재시도` 같은 제목. */
    private static final Pattern IN_HEADING = Pattern.compile("^(TASK-\\d+)\\s+(.+)$");

    /** Issue 제목의 `TASK-005: 수집·스냅샷·재시도` 접두사. */
    private static final Pattern IN_ISSUE_TITLE = Pattern.compile("^\\s*(TASK-\\d+)\\s*:\\s*(.*)$");

    /** PR 제목 어디에든 있는 작업 ID. `feat: TASK-005 …`처럼 종류가 앞에 오는 형식을 받는다. */
    private static final Pattern ANYWHERE = Pattern.compile("\\b(TASK-\\d+)\\b");

    private TaskIds() {
    }

    /** @param level 작업 단위로 인정할 제목 단계. 작업계획 문서는 `##`를 쓴다 */
    public record ExtractedTask(String taskSpecId, String title, String anchor) {
    }

    public static List<ExtractedTask> fromHeadings(List<DocumentHeading> headings, int level) {
        List<ExtractedTask> tasks = new ArrayList<>();
        for (DocumentHeading heading : headings) {
            if (heading.level() != level) {
                continue;
            }
            Matcher matcher = IN_HEADING.matcher(heading.text().trim());
            if (matcher.matches()) {
                // 제목 뒤에 붙은 `— 다음 작업` 같은 꼬리표는 작업 이름의 일부로 둔다.
                tasks.add(new ExtractedTask(matcher.group(1), matcher.group(2).trim(), heading.id()));
            }
        }
        return List.copyOf(tasks);
    }

    /** @return Issue 제목이 작업을 주장하면 그 ID. 접두사가 없으면 null */
    public static String fromIssueTitle(String title) {
        if (title == null) {
            return null;
        }
        Matcher matcher = IN_ISSUE_TITLE.matcher(title);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /** @return PR 제목이 가리키는 작업 ID. 없으면 null */
    public static String fromPullRequestTitle(String title) {
        if (title == null) {
            return null;
        }
        Matcher matcher = ANYWHERE.matcher(title);
        return matcher.find() ? matcher.group(1) : null;
    }
}
