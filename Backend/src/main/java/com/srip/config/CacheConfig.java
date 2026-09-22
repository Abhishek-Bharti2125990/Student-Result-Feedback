package com.srip.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application cache.
 *
 * <p>The original specification called for Redis here. Redis is a separate
 * server process, and the brief was to keep everything runnable locally with
 * nothing to install, so this uses Spring's in-process cache manager instead.
 * Nothing else in the code knows the difference: the services only use
 * {@code @Cacheable} / {@code @CacheEvict} against the cache names below.
 *
 * <p>To move to Redis later, add {@code spring-boot-starter-data-redis}, delete
 * this class, and set {@code spring.cache.type=redis} plus the host and port.
 * Boot then supplies a {@code RedisCacheManager} with the same interface, and
 * no service code changes.
 */
@Configuration
public class CacheConfig {

    /** Class rankings for one exam. Invalidated when a CSV upload completes. */
    public static final String CACHE_RANKINGS = "examRankings";

    /** Class-wide analytics for one exam. Invalidated on upload. */
    public static final String CACHE_CLASS_ANALYTICS = "classAnalytics";

    /** Generated feedback, which is expensive: every miss is a billed model call. */
    public static final String CACHE_FEEDBACK = "aiFeedback";

    @Bean
    public CacheManager cacheManager() {
        // Naming the caches up front means a typo in a @Cacheable name fails
        // fast rather than quietly creating a second, never-hit cache.
        return new ConcurrentMapCacheManager(
                CACHE_RANKINGS, CACHE_CLASS_ANALYTICS, CACHE_FEEDBACK);
    }
}
