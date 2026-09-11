package io.github.unclesamsun.syncdoc.github;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 자격증명이 있으면 실제 게이트웨이를, 없으면 명시적으로 실패하는 게이트웨이를 등록한다.
 * 조건을 한 곳에서 판단해 어떤 구현이 뜨는지가 설정 하나로 결정되게 한다.
 */
@Configuration
@EnableConfigurationProperties(GitHubProperties.class)
public class GitHubGatewayConfig {

    @Bean
    GitHubOAuthGateway gitHubOAuthGateway(GitHubProperties properties, RestClient.Builder builder, Clock clock) {
        return properties.isUserAuthConfigured()
                ? new GitHubAppOAuthGateway(properties, builder, clock)
                : new UnconfiguredGitHubOAuthGateway();
    }

    @Bean
    RepositoryAccessGateway repositoryAccessGateway(GitHubProperties properties, RestClient.Builder builder) {
        return properties.isUserAuthConfigured()
                ? new GitHubApiRepositoryAccessGateway(properties, builder)
                : new UnconfiguredRepositoryAccessGateway();
    }

    @Bean
    GitHubIdentityGateway gitHubIdentityGateway(GitHubProperties properties, RestClient.Builder builder) {
        return properties.isUserAuthConfigured()
                ? new GitHubApiIdentityGateway(properties, builder)
                : new UnconfiguredGitHubIdentityGateway();
    }

    /** 수집용 토큰이다. 사용자 로그인 설정이 아니라 App ID와 private key로 판단한다. */
    @Bean
    InstallationTokenGateway installationTokenGateway(GitHubProperties properties,
                                                      RestClient.Builder builder, Clock clock) {
        return properties.isAppAuthConfigured()
                ? new GitHubAppInstallationTokenGateway(properties, builder, clock)
                : new UnconfiguredInstallationTokenGateway();
    }

    @Bean
    RepositoryContentGateway repositoryContentGateway(GitHubProperties properties,
                                                      InstallationTokenGateway tokens,
                                                      RestClient.Builder builder) {
        return new GitHubApiRepositoryContentGateway(properties, tokens, builder);
    }
}
