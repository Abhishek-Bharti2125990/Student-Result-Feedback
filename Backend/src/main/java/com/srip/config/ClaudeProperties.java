package com.srip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Claude API settings, bound from {@code app.claude.*}.
 *
 * <p>When {@link #apiKey()} is blank the platform stays fully usable: feedback
 * endpoints fall back to the local rule-based writer instead of failing. That
 * keeps the app runnable without credentials.
 *
 * @param apiKey           Anthropic API key, normally supplied via {@code ANTHROPIC_API_KEY}
 * @param model            model id to call
 * @param maxTokens        output cap per request
 * @param timeout          per-request HTTP timeout
 * @param enabled          master switch; set false to force fallback mode
 * @param generateOnImport write every feedback document as the last step of a
 *                         CSV import, so dashboards are populated before anyone
 *                         opens them. Set false to pay for a model call only
 *                         when a dashboard is actually read.
 */
@ConfigurationProperties(prefix = "app.claude")
public record ClaudeProperties(
        String apiKey,
        String model,
        int maxTokens,
        Duration timeout,
        boolean enabled,
        boolean generateOnImport
) {

    public ClaudeProperties {
        model = (model == null || model.isBlank()) ? "claude-opus-5" : model;
        maxTokens = maxTokens <= 0 ? 8000 : maxTokens;
        timeout = timeout == null ? Duration.ofSeconds(120) : timeout;
    }

    /** True when a real API call can be attempted. */
    public boolean usable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
