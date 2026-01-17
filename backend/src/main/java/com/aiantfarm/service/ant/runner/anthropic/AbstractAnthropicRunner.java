package com.aiantfarm.service.ant.runner.anthropic;

import com.aiantfarm.domain.Ant;
import com.aiantfarm.service.ant.AntModelContext;
import com.aiantfarm.service.ant.IAntModelRunner;
import com.aiantfarm.service.ant.runner.ModelRunnerSupport;
import com.aiantfarm.service.ant.runner.PromptBuilder;
import com.aiantfarm.service.ant.runner.PromptTranscriptLogger;
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

  private final PromptTranscriptLogger transcriptLogger;

  private AnthropicClient client;

  protected AbstractAnthropicRunner(String apiKey,
                                    double temperature,
                                    int maxTokens,
                                    String modelId,
                                    PromptTranscriptLogger transcriptLogger) {
    this.apiKey = apiKey;
    this.temperature = temperature;
    this.maxTokens = maxTokens;
    this.modelId = modelId;
    this.transcriptLogger = transcriptLogger;
  }

  @PostConstruct
  void validate() {
    if (isBlank(apiKey)) {
      log.error("Anthropic API key missing. Set antfarm.models.anthropic.apiKey or env ANTHROPIC_API_KEY");
      throw new IllegalStateException("Anthropic API key missing");
    }

    // We do our own retries/backoff below, so keep SDK retries at 0 to avoid double-retrying.
    this.client = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .maxRetries(0)
        .build();
  }

  @Override
  public String generateMessage(Ant ant, String roomId, AntModelContext context) {
    long start = System.nanoTime();

    String system = PromptBuilder.buildSystemPrompt(ant.name(), ant.personalityPrompt(), maxTokens);

    boolean forceReply = context != null && "__FORCE_REPLY__".equals(context.bicameralThoughtJson());
    String userCtx = PromptBuilder.buildUserContext(
        context == null ? "" : context.roomScenario(),
        context == null ? "" : context.antPersonality(),
        context == null ? "" : context.roomRoleName(),
        context == null ? "" : context.roomRolePrompt(),
        context == null ? "" : context.roomSummary(),
        forceReply ? "" : (context == null ? "" : context.bicameralThoughtJson()),
        context == null ? null : context.recentMessages(),
        8_000,
        forceReply);

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

        // --- Prompt/response transcript logging (opt-in) ---
        if (transcriptLogger != null && transcriptLogger.enabled()) {
          transcriptLogger.logPromptAndResponse(
              ant,
              roomId,
              model(),
              "GenerateMessage",
              system,
              userCtx,
              out,
              latencyMs,
              inTok,
              outTok
          );
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

  @Override
  public String generateRoomSummary(Ant ant, String roomId, AntModelContext context, String existingSummary) {
    long start = System.nanoTime();

    String system = PromptBuilder.buildSummarySystemPrompt(ant.name(), ant.personalityPrompt(), 600);
    String user = PromptBuilder.buildSummaryUserPrompt(
        context == null ? "" : context.roomScenario(),
        context == null ? "" : context.antPersonality(),
        context == null ? "" : context.roomRoleName(),
        context == null ? "" : context.roomRolePrompt(),
        existingSummary,
        context == null ? null : context.recentMessages(),
        8_000);

    MessageCreateParams params = MessageCreateParams.builder()
        .model(modelId)
        .maxTokens(600L)
        .temperature(0.2)
        .system(system)
        .addUserMessage(user)
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
          logFailure(log, ant, roomId, model(), latencyMs, "BlankSummary", "Anthropic returned blank summary");
          throw new IllegalStateException("blank summary");
        }

        // --- Prompt/response transcript logging (opt-in) ---
        if (transcriptLogger != null && transcriptLogger.enabled()) {
          transcriptLogger.logPromptAndResponse(
              ant,
              roomId,
              model(),
              "GenerateRoomSummary",
              system,
              user,
              out,
              latencyMs,
              inTok,
              outTok
          );
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

  @Override
  public String generateBicameralThought(Ant ant, String roomId, AntModelContext context) {
    long start = System.nanoTime();

    String system = PromptBuilder.buildBicameralThoughtSystemPrompt(ant.name(), 500);
    String user = PromptBuilder.buildBicameralThoughtUserPrompt(
        context == null ? "" : context.roomScenario(),
        context == null ? "" : context.antPersonality(),
        context == null ? "" : context.roomRoleName(),
        context == null ? "" : context.roomRolePrompt(),
        context == null ? "" : context.roomSummary(),
        context == null ? null : context.recentMessages(),
        8_000);

    MessageCreateParams params = MessageCreateParams.builder()
        .model(modelId)
        .maxTokens(500L)
        .temperature(0.2)
        .system(system)
        .addUserMessage(user)
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
          logFailure(log, ant, roomId, model(), latencyMs, "BlankThought", "Anthropic returned blank thought JSON");
          throw new IllegalStateException("blank thought");
        }

        if (transcriptLogger != null && transcriptLogger.enabled()) {
          transcriptLogger.logPromptAndResponse(
              ant,
              roomId,
              model(),
              "GenerateBicameralThought",
              system,
              user,
              out,
              latencyMs,
              inTok,
              outTok
          );
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
