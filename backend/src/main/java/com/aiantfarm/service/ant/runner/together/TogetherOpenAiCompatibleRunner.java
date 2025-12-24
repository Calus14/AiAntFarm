package com.aiantfarm.service.ant.runner.together;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import com.aiantfarm.service.ant.AntModelContext;
import com.aiantfarm.service.ant.IAntModelRunner;
import com.aiantfarm.service.ant.runner.ModelRunnerSupport;
import com.aiantfarm.service.ant.runner.PromptBuilder;
import com.aiantfarm.service.ant.runner.RetryUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * Together AI is OpenAI-compatible. We call /v1/chat/completions directly.
 */
@Component
@Slf4j
public class TogetherOpenAiCompatibleRunner extends ModelRunnerSupport implements IAntModelRunner {

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final ObjectMapper mapper = new ObjectMapper();

  private final String apiKey;
  private final String baseUrl;
  private final String modelId;
  private final long timeoutMs;
  private final double temperature;
  private final int maxTokens;

  private OkHttpClient client;

  public TogetherOpenAiCompatibleRunner(
      @Value("${antfarm.models.together.apiKey:${TOGETHER_API_KEY:}}") String apiKey,
      @Value("${antfarm.models.together.baseUrl:https://api.together.xyz/v1}") String baseUrl,
      @Value("${antfarm.models.together.model.llamaSmall:meta-llama/Llama-3.1-8B-Instruct-Turbo}") String modelId,
      @Value("${antfarm.models.together.timeoutMs:20000}") long timeoutMs,
      @Value("${antfarm.models.together.temperature:0.7}") double temperature,
      @Value("${antfarm.models.together.maxTokens:256}") int maxTokens
  ) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
    this.modelId = modelId;
    this.timeoutMs = timeoutMs;
    this.temperature = temperature;
    this.maxTokens = maxTokens;
  }

  @PostConstruct
  void validate() {
//    if (isBlank(apiKey)) {
//      log.error("Together API key missing. Set antfarm.models.together.apiKey or env TOGETHER_API_KEY");
//      throw new IllegalStateException("Together API key missing");
//    }
//
//    this.client = new OkHttpClient.Builder()
//        .callTimeout(Duration.ofMillis(timeoutMs))
//        .build();
  }

  @Override
  public AiModel model() {
    return AiModel.TOGETHER_LLAMA_SMALL;
  }

  @Override
  public String generateMessage(Ant ant, String roomId, AntModelContext context) {
    long start = System.nanoTime();

    String system = PromptBuilder.buildSystemPrompt(ant.name(), ant.personalityPrompt());
    String userCtx = PromptBuilder.buildUserContext(context == null ? null : context.recentMessages(), 8_000);

    String payload = "{"
        + "\"model\":" + mapper.valueToTree(modelId) + ","
        + "\"temperature\":" + temperature + ","
        + "\"max_tokens\":" + maxTokens + ","
        + "\"messages\":["
        + "{\"role\":\"system\",\"content\":" + mapper.valueToTree(system) + "},"
        + "{\"role\":\"user\",\"content\":" + mapper.valueToTree(userCtx) + "}"
        + "]"
        + "}";

    String url = baseUrl.endsWith("/") ? (baseUrl + "chat/completions") : (baseUrl + "/chat/completions");

    int maxAttempts = 3;
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      Request req = new Request.Builder()
          .url(url)
          .addHeader("Authorization", "Bearer " + apiKey)
          .post(RequestBody.create(payload, JSON))
          .build();

      try (Response resp = client.newCall(req).execute()) {
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        int code = resp.code();

        String body = resp.body() == null ? "" : resp.body().string();

        if (code == 401 || code == 403) {
          logFailure(log, ant, roomId, model(), latencyMs, "Auth", "Unauthorized");
          throw new IllegalStateException("Together auth failed");
        }

        if (code == 429 || code >= 500) {
          logFailure(log, ant, roomId, model(), latencyMs, "Http" + code, body);
          if (attempt == maxAttempts - 1) throw new IllegalStateException("Together error code=" + code);
          RetryUtil.sleepBackoff(attempt, 250, 2_000);
          continue;
        }

        if (code < 200 || code >= 300) {
          logFailure(log, ant, roomId, model(), latencyMs, "Http" + code, body);
          throw new IllegalStateException("Together non-2xx code=" + code);
        }

        JsonNode root = mapper.readTree(body);
        JsonNode choice0 = root.path("choices").isArray() && root.path("choices").size() > 0 ? root.path("choices").get(0) : null;
        String out = choice0 == null ? null : choice0.path("message").path("content").asText(null);

        Integer inTok = root.path("usage").path("prompt_tokens").isNumber() ? root.path("usage").path("prompt_tokens").asInt() : null;
        Integer outTok = root.path("usage").path("completion_tokens").isNumber() ? root.path("usage").path("completion_tokens").asInt() : null;

        if (isBlank(out)) {
          logFailure(log, ant, roomId, model(), latencyMs, "BlankResponse", "Together returned blank content");
          throw new IllegalStateException("blank response");
        }

        logSuccess(log, ant, roomId, model(), latencyMs, inTok, outTok);
        return out.trim();
      } catch (IOException ioe) {
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        logFailure(log, ant, roomId, model(), latencyMs, ioe.getClass().getSimpleName(), ioe.getMessage());
        if (attempt == maxAttempts - 1) throw new RuntimeException(ioe);
        RetryUtil.sleepBackoff(attempt, 250, 2_000);
      } catch (RuntimeException re) {
        if (attempt == maxAttempts - 1) throw re;
        RetryUtil.sleepBackoff(attempt, 250, 2_000);
      } catch (Exception e) {
        if (attempt == maxAttempts - 1) throw new RuntimeException(e);
        RetryUtil.sleepBackoff(attempt, 250, 2_000);
      }
    }

    throw new IllegalStateException("unreachable");
  }
}

