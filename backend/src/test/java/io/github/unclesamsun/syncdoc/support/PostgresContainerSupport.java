package io.github.unclesamsun.syncdoc.support;

import io.github.unclesamsun.syncdoc.TestcontainersConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * PostgreSQL 컨테이너 하나를 띄우고 Spring datasource를 자동 연결하는 통합 테스트 부모.
 * 컨테이너 정의는 생성기가 만든 {@link TestcontainersConfiguration}에 있다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresContainerSupport {
}
