package io.github.unclesamsun.syncdoc.sync;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(SyncProperties.class)
@EnableScheduling
public class SyncConfig {

    @Bean
    RetryPolicy retryPolicy(SyncProperties properties) {
        return new RetryPolicy(properties);
    }
}
