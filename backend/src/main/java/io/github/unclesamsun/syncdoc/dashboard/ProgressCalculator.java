package io.github.unclesamsun.syncdoc.dashboard;

import io.github.unclesamsun.syncdoc.dashboard.domain.IssueSnapshotEntity;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 작업 상태와 완료율을 센다.
 *
 * <p>규칙은 [협업 규칙 §7]이다. 완료율은 확정 작업 중 완료한 수 / 취소를 뺀 확정 작업 수이며,
 * Issue가 없다는 이유로 작업이 분모에서 빠지지 않는다. 그렇게 빼면 등록을 미룰수록 완료율이
 * 올라가는 값이 된다.
 *
 * <p>PR 수나 커밋 수는 세지 않는다. 한 작업에 PR이 여럿일 수 있고, PR을 세면 같은 작업을 여러 번
 * 완료한 것으로 집계된다.
 */
@Component
public class ProgressCalculator {

    /** Issue가 없는 작업. 목록에서 사라지지 않고 이 상태로 보인다. */
    public static final String UNREGISTERED = "unregistered";
    public static final String IN_PROGRESS = "in_progress";
    public static final String DONE = "done";
    public static final String CANCELED = "canceled";

    /**
     * @param denominator 취소를 뺀 확정 작업 수. 0이면 계산 대상이 없다
     * @param ratio       0~1. 분모가 0이면 null이며 0으로 대체하지 않는다
     */
    public record Progress(int completed, int denominator, Double ratio) {
    }

    public record Counts(int notStarted, int inProgress, int inReview, int done, int unregistered,
                         int canceled, int total) {
    }

    /** 작업 하나의 상태. Issue가 정본이며 없으면 미등록이다. */
    public String statusOf(IssueSnapshotEntity issue) {
        if (issue == null) {
            return UNREGISTERED;
        }
        if (issue.isCanceled()) {
            return CANCELED;
        }
        if (issue.isCompleted()) {
            return DONE;
        }
        return IN_PROGRESS;
    }

    public Counts count(List<String> statuses) {
        Map<String, Integer> byStatus = new java.util.HashMap<>();
        statuses.forEach(status -> byStatus.merge(status, 1, Integer::sum));
        return new Counts(
                // 미착수와 리뷰는 Project 상태를 읽어야 알 수 있다. Issue만으로는 구분하지 않는다.
                0,
                byStatus.getOrDefault(IN_PROGRESS, 0),
                0,
                byStatus.getOrDefault(DONE, 0),
                byStatus.getOrDefault(UNREGISTERED, 0),
                byStatus.getOrDefault(CANCELED, 0),
                statuses.size());
    }

    /**
     * 완료율. 취소는 분자에서도 분모에서도 뺀다.
     *
     * @return 분모가 0이면 {@code ratio}가 null인 Progress. 계산 대상 없음이라는 뜻이며 0%가 아니다
     */
    public Progress progress(Counts counts) {
        int denominator = counts.total() - counts.canceled();
        if (denominator <= 0) {
            return new Progress(counts.done(), 0, null);
        }
        return new Progress(counts.done(), denominator, (double) counts.done() / denominator);
    }
}
