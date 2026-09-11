package io.github.unclesamsun.syncdoc.support;

import io.github.unclesamsun.syncdoc.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * PostgreSQL 컨테이너 하나를 띄우고 Spring datasource를 자동 연결하는 통합 테스트 부모.
 * 컨테이너 정의는 생성기가 만든 {@link TestcontainersConfiguration}에 있다.
 *
 * <p>컨테이너 하나를 여러 테스트가 나눠 쓰므로 각 테스트 전에 신원 테이블을 비운다.
 * 그러지 않으면 한 테스트가 넣은 GitHub 사용자 ID가 다음 테스트의 unique 제약에 걸린다.
 *
 * <p>GitHub 자격증명은 일부러 비워 둔다. 미설정 게이트웨이가 명시적으로 실패하는지도 검사 대상이다.
 */
@Import({TestcontainersConfiguration.class, TestFixtures.class})
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = {
        "syncdoc.auth.cookie-secure=false",
        "syncdoc.auth.csrf-key=test-csrf-key",
        "syncdoc.crypto.token-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        // 배경 worker가 임의의 시점에 끼어들면 무엇이 언제 일어났는지 검증할 수 없다.
        // 수집을 검사하는 테스트는 worker를 직접 호출한다.
        "syncdoc.sync.worker-enabled=false",
        "syncdoc.sync.webhook-secret=test-webhook-secret",
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearIdentityTables() {
        jdbcTemplate.execute("truncate table sessions, user_credentials, documents, document_snapshots, "
                + "sync_runs, sync_jobs, webhook_deliveries, projects, github_installations, invitations, users cascade");
    }
}
