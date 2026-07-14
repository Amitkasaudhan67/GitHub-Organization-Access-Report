package com.github.accessreport.config;

import com.github.accessreport.dto.AccessReportResponse;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures the bounded asynchronous cache for complete organization reports. */
@Configuration
public class CacheConfig {
    @Bean
    AsyncCache<String, List<AccessReportResponse>> accessReportCache(GithubProperties properties) {
        return Caffeine.newBuilder().maximumSize(100).expireAfterWrite(properties.reportCacheTtl()).buildAsync();
    }
}
