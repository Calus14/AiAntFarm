package com.aiantfarm.service.ant.runner.anthropic;

import com.aiantfarm.domain.Ant;
import com.aiantfarm.service.ant.AntModelContext;
import com.aiantfarm.service.ant.IAntModelRunner;
import com.aiantfarm.service.ant.runner.ModelRunnerSupport;
import com.aiantfarm.service.ant.runner.PromptBuilder;
import com.aiantfarm.service.ant.runner.RetryUtil;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared Anthropic runner implementation.
 */
@Slf4j
public abstract class AbstractAnthropicRunner extends ModelRunnerSupport implements IAntModelRunner {

  private final String apiKey;
  private final String modelId;
  private final int maxTokens;
  private final double temperature;

  private AnthropicClient client;

  protected AbstractAnthropicRunner(String apiKey,
                                    double temperature,
                                    int maxTokens,
                                    String modelId) {
    this.apiKey = apiKey;
    this.temperature = temperature;
    this.maxTokens = maxTokens;
    this.modelId = modelId;
  }

  @PostConstruct
  void validate() {
//    if (isBlank(apiKey)) {
//      log.error("Anthropic API key missing. Set antfarm.models.anthropic.apiKey or env ANTHROPIC_API_KEY");
//      throw new IllegalStateException("Anthropic API key missing");
//    }
//
//    // We do our own retries/backoff below, so keep SDK retries at 0 to avoid double-retrying.
//    this.client = AnthropicOkHttpClient.builder()
//        .apiKey(apiKey)
//        .maxRetries(0)
//        .build();
  }

  @Override
  public String generateMessage(Ant ant, String roomId, AntModelContext context) {
    long start = System.nanoTime();

    String system = PromptBuilder.buildSystemPrompt(ant.name(), ant.personalityPrompt());
    String userCtx = PromptBuilder.buildUserContext(context == null ? null : context.recentMessages(), 8_000);

    MessageCreateParams params = MessageCreateParams.builder()
        .model(modelId)                 // SDK supports model(String)
        .maxTokens((long) maxTokens)    // SDK expects Long
        .temperature(temperature)
        .system(system)
        .addUserMessage(userCtx)
        .build();

    int maxAttempts = 3;
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        Message resp = client.messages().create(params);

        String out = extractText(resp);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        Integer inTok = resp.usage() != null ? (int) resp.usage().inputTokens() : null;
        Integer outTok = resp.usage() != null ? (int) resp.usage().outputTokens() : null;

        if (isBlank(out)) {
          logFailure(log, ant, roomId, model(), latencyMs, "BlankResponse", "Anthropic returned blank content");
          throw new IllegalStateException("blank response");
        }

        logSuccess(log, ant, roomId, model(), latencyMs, inTok, outTok);
        return out.trim();

      } catch (UnauthorizedException e) {
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        logFailure(log, ant, roomId, model(), latencyMs, e.getClass().getSimpleName(), "auth failed");
        throw e;

      } catch (RateLimitException | AnthropicIoException | AnthropicRetryableException | InternalServerException e) {
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        logFailure(log, ant, roomId, model(), latencyMs, e.getClass().getSimpleName(), e.getMessage());
        if (attempt == maxAttempts - 1) throw e;
        RetryUtil.sleepBackoff(attempt, 250, 2_000);

      } catch (Exception e) {
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        logFailure(log, ant, roomId, model(), latencyMs, e.getClass().getSimpleName(), e.getMessage());
        if (attempt == maxAttempts - 1) throw new RuntimeException(e);
        RetryUtil.sleepBackoff(attempt, 250, 2_000);
      }
    }

    throw new IllegalStateException("unreachable");
  }

  private static String extractText(Message message) {
    if (message == null || message.content() == null || message.content().isEmpty()) return null;

    StringBuilder sb = new StringBuilder();
    for (ContentBlock block : message.content()) {
      if (block != null && block.isText()) {
        String t = block.asText().text();
        if (t != null) sb.append(t);
      }
    }
    String out = sb.toString();
    return out.isBlank() ? null : out;
  }
}
