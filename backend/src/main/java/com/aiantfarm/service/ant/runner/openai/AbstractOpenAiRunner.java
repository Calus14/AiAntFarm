package com.aiantfarm.service.ant.runner.openai;

import com.aiantfarm.domain.Ant;
import com.aiantfarm.service.ant.AntModelContext;
import com.aiantfarm.service.ant.IAntModelRunner;
import com.aiantfarm.service.ant.runner.ModelRunnerSupport;
import com.aiantfarm.service.ant.runner.PromptBuilder;
import com.aiantfarm.service.ant.runner.RetryUtil;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared OpenAI runner implementation.
 */
@Slf4j
public abstract class AbstractOpenAiRunner extends ModelRunnerSupport implements IAntModelRunner {

  private final String apiKey;
  private final String modelId;
  private final int maxTokens;
  private final double temperature;

  private OpenAIClient client;

  protected AbstractOpenAiRunner(String apiKey,
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
    if (isBlank(apiKey)) {
      log.error("OpenAI API key missing. Set antfarm.models.openai.apiKey or env OPENAI_API_KEY");
      throw new IllegalStateException("OpenAI API key missing");
    }

    // openai-java 4.x client
    this.client = OpenAIOkHttpClient.builder()
        .apiKey(apiKey)
        // keep retries centralized in our RetryUtil loop
        .maxRetries(0)
        .build();
  }

  @Override
  public String generateMessage(Ant ant, String roomId, AntModelContext context) {
    long start = System.nanoTime();

    String system = PromptBuilder.buildSystemPrompt(ant.name(), ant.personalityPrompt());
    String userCtx = PromptBuilder.buildUserContext(
        context == null ? "" : context.roomSummary(),
        context == null ? null : context.recentMessages(),
        8_000);

    ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
        .model(modelId)
        .temperature(temperature)
        .maxCompletionTokens((long) maxTokens)
        .addMessage(ChatCompletionSystemMessageParam.builder().content(system).build())
        .addMessage(ChatCompletionUserMessageParam.builder().content(userCtx).build())
        .build();

    return callWithRetry(ant, roomId, start, params, "BlankResponse", "OpenAI returned blank content");
  }

  @Override
  public String generateRoomSummary(Ant ant, String roomId, AntModelContext context, String existingSummary) {
    long start = System.nanoTime();

    String system = PromptBuilder.buildSummarySystemPrompt(ant.name(), ant.personalityPrompt());
    String user = PromptBuilder.buildSummaryUserPrompt(
        context == null ? "" : context.roomScenario(),
        existingSummary,
        context == null ? null : context.recentMessages(),
        8_000);

    ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
        .model(modelId)
        .temperature(0.2)
        .maxCompletionTokens((long) Math.min(maxTokens, 600))
        .addMessage(ChatCompletionSystemMessageParam.builder().content(system).build())
        .addMessage(ChatCompletionUserMessageParam.builder().content(user).build())
        .build();

    return callWithRetry(ant, roomId, start, params, "BlankSummary", "OpenAI returned blank summary");
  }

  private String callWithRetry(Ant ant,
                              String roomId,
                              long startNano,
                              ChatCompletionCreateParams params,
                              String blankCode,
                              String blankMsg) {
    int maxAttempts = 3; // 1 + 2 retries
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        ChatCompletion cc = client.chat().completions().create(params);
        String out = String.valueOf(cc.choices().get(0).message().content());

        long latencyMs = (System.nanoTime() - startNano) / 1_000_000;

        long inTok = 0;
        long outTok = 0;
        try {
          if (cc.usage().isPresent()) {
            inTok = cc.usage().get().promptTokens();
            outTok = cc.usage().get().completionTokens();
          }
        } catch (Exception ignore) {
        }

        if (isBlank(out)) {
          logFailure(log, ant, roomId, model(), latencyMs, blankCode, blankMsg);
          throw new IllegalStateException("blank response");
        }

        logSuccess(log, ant, roomId, model(), latencyMs, (int) inTok, (int) outTok);
        return out.trim();

      } catch (UnauthorizedException e) {
        long latencyMs = (System.nanoTime() - startNano) / 1_000_000;
        logFailure(log, ant, roomId, model(), latencyMs, e.getClass().getSimpleName(), "auth failed");
        throw e;

      } catch (RateLimitException | OpenAIIoException | OpenAIRetryableException e) {
        long latencyMs = (System.nanoTime() - startNano) / 1_000_000;
        logFailure(log, ant, roomId, model(), latencyMs, e.getClass().getSimpleName(), e.getMessage());
        if (attempt == maxAttempts - 1) throw e;
        RetryUtil.sleepBackoff(attempt, 250, 2_000);

      } catch (Exception e) {
        long latencyMs = (System.nanoTime() - startNano) / 1_000_000;
        logFailure(log, ant, roomId, model(), latencyMs, e.getClass().getSimpleName(), e.getMessage());
        if (attempt == maxAttempts - 1) throw new RuntimeException(e);
        RetryUtil.sleepBackoff(attempt, 250, 2_000);
      }
    }

    throw new IllegalStateException("unreachable");
  }
}
