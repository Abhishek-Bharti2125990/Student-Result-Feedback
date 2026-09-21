package com.srip.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.srip.config.ClaudeProperties;
import com.srip.exception.ApiExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The only place that talks to the Claude API.
 *
 * <p>Two decisions are worth explaining.
 *
 * <p>First, every call uses <em>structured outputs</em>: the response format is
 * a JSON schema derived from the target record, so the API cannot return prose
 * that fails to parse. Nothing here scrapes text or repairs half-written JSON.
 *
 * <p>Second, the system prompt is sent as a cacheable block. It is identical
 * for every student, so after the first call in a window the API serves it from
 * its prompt cache at roughly a tenth of the input cost - and generating
 * feedback for a class of forty means forty calls sharing that same prefix.
 */
@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    private final ClaudeProperties properties;
    private final AnthropicClient client;

    public ClaudeClient(ClaudeProperties properties) {
        this.properties = properties;
        this.client = properties.usable() ? buildClient(properties) : null;

        if (this.client == null) {
            log.warn("Claude API key is not configured - feedback endpoints will use the local "
                    + "rule-based writer. Set ANTHROPIC_API_KEY to enable model-generated feedback.");
        }
    }

    private static AnthropicClient buildClient(ClaudeProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                // Generating feedback is a background-ish operation, so a
                // transient 429 or 5xx is worth retrying rather than surfacing.
                .maxRetries(2)
                .build();
    }

    /** False when no API key is configured; callers then use the fallback writer. */
    public boolean isAvailable() {
        return client != null;
    }

    public String model() {
        return properties.model();
    }

    /**
     * Sends one request and returns the response parsed into {@code type}.
     *
     * @param type         the record the response must conform to; its fields and
     *                     {@code @JsonPropertyDescription} text become the schema
     * @param systemPrompt stable instructions, sent as a cacheable prefix
     * @param userPrompt   the per-student data for this call
     */
    public <T> Generated<T> generate(Class<T> type, String systemPrompt, String userPrompt) {
        if (client == null) {
            throw new IllegalStateException("Claude client is not configured; check isAvailable() first");
        }

        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(systemPrompt)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(userPrompt)
                // Must be last: this overload switches the builder to the typed one.
                .outputConfig(type)
                .build();

        try {
            StructuredMessage<T> response = client.messages().create(params);

            // A safety decline returns HTTP 200 with stop_reason "refusal", so
            // stop_reason has to be checked before the content is read.
            if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                String explanation = response.stopDetails()
                        .map(details -> String.valueOf(details.explanation()))
                        .orElse("no explanation given");
                throw new ApiExceptions.AiGenerationException(
                        "Claude declined to generate this feedback: " + explanation, null);
            }

            T value = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(StructuredTextBlock::text)
                    .findFirst()
                    .orElseThrow(() -> new ApiExceptions.AiGenerationException(
                            "Claude returned no text content", null));

            return new Generated<>(
                    value,
                    properties.model(),
                    (int) response.usage().inputTokens(),
                    (int) response.usage().outputTokens());

        } catch (AnthropicServiceException e) {
            throw new ApiExceptions.AiGenerationException(
                    "Claude API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * A generated document plus what it cost.
     *
     * @param inputTokens  billed input tokens, logged so prompt-cache savings are visible
     * @param outputTokens billed output tokens
     */
    public record Generated<T>(T value, String model, int inputTokens, int outputTokens) {
    }
}
