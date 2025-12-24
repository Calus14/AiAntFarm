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
    String userCtx = PromptBuilder.buildUserContext(context == null ? null : context.recentMessages(), 8_000);

    ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
        .model(modelId)
        .temperature(temperature)
        .maxTokens((long) maxTokens)
        .addSystemMessage(system)
        .addUserMessage(userCtx)
        .build();

    int maxAttempts = 3; // 1 + 2 retries
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        ChatCompletion cc = client.chat().completions().create(params);

        String out = null;
        if (cc.choices() != null && !cc.choices().isEmpty() && cc.choices().get(0).message() != null) {
          out = String.valueOf(cc.choices().get(0).message().content());
        }

        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        // Token usage fields can vary by model / response shape; keep logging resilient.
        Long inTok = 0L;
        Long outTok = 0L;
        try {
          if (cc.usage() != null && cc.usage().isPresent()) {
            inTok = cc.usage().get().promptTokens();
            outTok = cc.usage().get().completionTokens();
          }
        } catch (Exception ignore) {
          // ignore usage extraction failures
        }

        if (isBlank(out)) {
          logFailure(log, ant, roomId, model(), latencyMs, "BlankResponse", "OpenAI returned blank content");
          throw new IllegalStateException("blank response");
        }

        logSuccess(log, ant, roomId, model(), latencyMs, inTok.intValue(), outTok.intValue());
        return out.trim();

      } catch (UnauthorizedException e) {
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        logFailure(log, ant, roomId, model(), latencyMs, e.getClass().getSimpleName(), "auth failed");
        throw e;

      } catch (RateLimitException | OpenAIIoException | OpenAIRetryableException e) {
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
}
