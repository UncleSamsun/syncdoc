package io.github.unclesamsun.syncdoc.sync;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 수집 동작 값. 데이터 설계가 제안한 임대 2분·heartbeat 30초·재시도 60초~15분과
 * REQ-006의 주기 조회 60초를 기본값으로 둔다. 이 값들은 갱신 완료 SLA가 아니다.
 *
 * @param leaseDuration   worker가 작업을 쥐고 있다고 인정하는 시간. 지나면 다른 worker가 가져간다
 * @param heartbeat       수집 중 임대를 연장하는 간격
 * @param pollInterval    이벤트가 없어도 저장소를 확인하는 간격
 * @param minRetryDelay   실패 후 첫 재시도 간격
 * @param maxRetryDelay   재시도 간격 상한
 * @param maxDocuments    한 게시본에 담을 문서 수 상한
 * @param maxDocumentSize 문서 하나의 원문 크기 상한
 * @param maxPayloadBytes webhook 본문 크기 상한. 넘으면 413이다
 * @param webhookSecret   webhook 서명 검증 비밀값. 없으면 서명을 확인할 수 없어 받지 않는다
 * @param workerEnabled   이 프로세스가 작업을 실행할지. API와 worker를 나눠 띄울 때 쓴다
 */
@ConfigurationProperties(prefix = "syncdoc.sync")
public record SyncProperties(
        Duration leaseDuration,
        Duration heartbeat,
        Duration pollInterval,
        Duration minRetryDelay,
        Duration maxRetryDelay,
        int maxDocuments,
        int maxDocumentSize,
        int maxPayloadBytes,
        String webhookSecret,
        Boolean workerEnabled) {

    public SyncProperties {
        leaseDuration = orDefault(leaseDuration, Duration.ofMinutes(2));
        heartbeat = orDefault(heartbeat, Duration.ofSeconds(30));
        pollInterval = orDefault(pollInterval, Duration.ofSeconds(60));
        minRetryDelay = orDefault(minRetryDelay, Duration.ofSeconds(60));
        maxRetryDelay = orDefault(maxRetryDelay, Duration.ofMinutes(15));
        maxDocuments = maxDocuments > 0 ? maxDocuments : 1000;
        maxDocumentSize = maxDocumentSize > 0 ? maxDocumentSize : 1024 * 1024;
        maxPayloadBytes = maxPayloadBytes > 0 ? maxPayloadBytes : 1024 * 1024;
        workerEnabled = workerEnabled == null || workerEnabled;
    }

    public boolean isWebhookConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
