package com.aiantfarm.service.ant.runner.gemini;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import com.aiantfarm.service.ant.AntModelContext;
import com.aiantfarm.service.ant.IAntModelRunner;
import com.aiantfarm.service.ant.runner.ModelRunnerSupport;
import com.aiantfarm.service.ant.runner.PromptBuilder;
import com.aiantfarm.service.ant.runner.RetryUtil;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Shared Gemini runner implementation (google-genai).
 */
@Slf4j
public abstract class AbstractGeminiRunner extends ModelRunnerSupport implements IAntModelRunner {

  private final String apiKey;
  private final String modelId;
  private final int maxTokens;
  private final double temperature;

  private Client client;

  protected AbstractGeminiRunner(String apiKey,
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
//      log.error("Gemini API key missing. Set antfarm.models.gemini.apiKey or env GEMINI_API_KEY");
//      throw new IllegalStateException("Gemini API key missing");
//    }
//    this.client = Client.builder().apiKey(apiKey).build();
  }

  @Override
  public String generateMessage(Ant ant, String roomId, AntModelContext context) {
    long start = System.nanoTime();

    String system = PromptBuilder.buildSystemPrompt(ant.name(), ant.personalityPrompt());
    String userCtx = PromptBuilder.buildUserContext(context == null ? null : context.recentMessages(), 8_000);
    String prompt = "SYSTEM:\n" + system + "\n\nCONTEXT:\n" + userCtx;

    GenerateContentConfig cfg = GenerateContentConfig.builder()
        .temperature((float) temperature)
        .maxOutputTokens(maxTokens)
        .build();

    List<Content> contents = List.of(
        Content.builder().role("user").parts(List.of(Part.fromText(prompt))).build()
    );

    int maxAttempts = 3;
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        GenerateContentResponse resp = client.models.generateContent(modelId, contents, cfg);
        String out = resp.text();

        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        if (isBlank(out)) {
          logFailure(log, ant, roomId, model(), latencyMs, "BlankResponse", "Gemini returned blank content");
          throw new IllegalStateException("blank response");
        }

        // Token counts not consistently available; log as unknown.
        logSuccess(log, ant, roomId, model(), latencyMs, null, null);
        return out.trim();
      } catch (Exception e) {
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        boolean auth = msg.contains("api key") || msg.contains("permission") || msg.contains("unauthorized");

        logFailure(log, ant, roomId, model(), latencyMs, e.getClass().getSimpleName(), e.getMessage());
        if (auth) throw new RuntimeException(e);
        if (attempt == maxAttempts - 1) throw new RuntimeException(e);
        RetryUtil.sleepBackoff(attempt, 250, 2_000);
      }
    }

    throw new IllegalStateException("unreachable");
  }
}

