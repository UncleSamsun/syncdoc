package io.github.unclesamsun.syncdoc.github;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 실제 구현이 등록되기 전까지 미설정 게이트웨이를 둔다. TASK-002가 자격증명이 있을 때의 구현을 더한다. */
@Configuration
public class GitHubGatewayConfig {

    @Bean
    @ConditionalOnMissingBean(GitHubIdentityGateway.class)
    GitHubIdentityGateway unconfiguredGitHubIdentityGateway() {
        return new UnconfiguredGitHubIdentityGateway();
    }
}
