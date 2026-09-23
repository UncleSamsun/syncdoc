package io.github.unclesamsun.syncdoc.support;

import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.project.domain.InstallationRepository;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** 통합 테스트가 함께 쓰는 준비 코드 빈. */
@TestConfiguration
public class TestFixtures {

    @Bean
    ProjectFixture projectFixture(ProjectRepository projects, InstallationRepository installations,
                                  UserRepository users) {
        return new ProjectFixture(projects, installations, users);
    }
}
