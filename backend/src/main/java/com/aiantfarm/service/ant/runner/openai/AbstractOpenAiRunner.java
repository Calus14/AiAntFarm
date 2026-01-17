package com.aiantfarm.service.ant.runner.openai;

import com.aiantfarm.domain.Ant;
import com.aiantfarm.service.ant.AntModelContext;
import com.aiantfarm.service.ant.IAntModelRunner;
import com.aiantfarm.service.ant.runner.ModelRunnerSupport;
import com.aiantfarm.service.ant.runner.PromptBuilder;
import com.aiantfarm.service.ant.runner.PromptTranscriptLogger;
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

  private final int summaryMaxTokens;
  private final int summaryMaxTokensCap;
  private final int thoughtMaxTokens;
  private final int thoughtMaxTokensCap;

  private final int maxAttempts;

  private final PromptTranscriptLogger transcriptLogger;

  private OpenAIClient client;

  protected AbstractOpenAiRunner(String apiKey,
                                 double temperature,
                                 int maxTokens,
                                 String modelId,
                                 int summaryMaxTokens,
                                 int summaryMaxTokensCap,
                                 int thoughtMaxTokens,
                                 int thoughtMaxTokensCap,
                                 int maxAttempts,
                                 PromptTranscriptLogger transcriptLogger) {
    this.apiKey = apiKey;
    this.temperature = temperature;
    this.maxTokens = maxTokens;
    this.modelId = modelId;
    this.summaryMaxTokens = summaryMaxTokens;
    this.summaryMaxTokensCap = summaryMaxTokensCap;
    this.thoughtMaxTokens = thoughtMaxTokens;
    this.thoughtMaxTokensCap = thoughtMaxTokensCap;
    this.maxAttempts = maxAttempts;
    this.transcriptLogger = transcriptLogger;
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

    String system = PromptBuilder.buildSystemPrompt(ant.name(), ant.personalityPrompt(), maxTokens);

    // --- NOTE (forceReply): temporary workaround using sentinel string ---
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

    ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
        .model(modelId)
        .temperature(temperature)
        .maxCompletionTokens((long) maxTokens*2)
        .addMessage(ChatCompletionSystemMessageParam.builder().content(system).build())
        .addMessage(ChatCompletionUserMessageParam.builder().content(userCtx).build())
        .build();

    return callWithRetry(ant, roomId, start, "GenerateMessage", system, userCtx, params,
        "BlankResponse", "OpenAI returned blank content");
  }

  @Override
  public String generateRoomSummary(Ant ant, String roomId, AntModelContext context, String existingSummary) {
    long start = System.nanoTime();

    int summaryMax = Math.max(maxTokens, this.summaryMaxTokens);
    int summaryMaxCap = Math.min(summaryMax, this.summaryMaxTokensCap);
    String system = PromptBuilder.buildSummarySystemPrompt(ant.name(), ant.personalityPrompt(), summaryMaxCap);
    String user = PromptBuilder.buildSummaryUserPrompt(
        context == null ? "" : context.roomScenario(),
        context == null ? "" : context.antPersonality(),
        context == null ? "" : context.roomRoleName(),
        context == null ? "" : context.roomRolePrompt(),
        existingSummary,
        context == null ? null : context.recentMessages(),
        8_000);

    // Summaries can be longer than messages; allow some headroom beyond the global maxTokens,
    // but keep it conservative to avoid page-length outputs.
     ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
         .model(modelId)
         .temperature(0.2)
         .maxCompletionTokens((long) summaryMaxCap*2)
         .addMessage(ChatCompletionSystemMessageParam.builder().content(system).build())
         .addMessage(ChatCompletionUserMessageParam.builder().content(user).build())
         .build();

     return callWithRetry(ant, roomId, start, "GenerateRoomSummary", system, user, params,
         "BlankSummary", "OpenAI returned blank summary");
   }

  @Override
  public String generateBicameralThought(Ant ant, String roomId, AntModelContext context) {
    long start = System.nanoTime();

    int thoughtMax = Math.max(maxTokens, this.thoughtMaxTokens);
    int thoughtMaxCap = Math.min(thoughtMax, this.thoughtMaxTokensCap);
    String system = PromptBuilder.buildBicameralThoughtSystemPrompt(ant.name(), thoughtMaxCap);
    String user = PromptBuilder.buildBicameralThoughtUserPrompt(
        context == null ? "" : context.roomScenario(),
        context == null ? "" : context.antPersonality(),
        context == null ? "" : context.roomRoleName(),
        context == null ? "" : context.roomRolePrompt(),
        context == null ? "" : context.roomSummary(),
        context == null ? null : context.recentMessages(),
        8_000);

    // Thoughts can also be moderately long JSON, but keep it bounded.
     ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
         .model(modelId)
         .temperature(0.2)
         .maxCompletionTokens((long) thoughtMaxCap*2)
         .addMessage(ChatCompletionSystemMessageParam.builder().content(system).build())
         .addMessage(ChatCompletionUserMessageParam.builder().content(user).build())
         .build();

     return callWithRetry(ant, roomId, start, "GenerateBicameralThought", system, user, params,
         "BlankThought", "OpenAI returned blank thought JSON");
   }

  private String callWithRetry(Ant ant,
                              String roomId,
                              long startNano,
                              String operation,
                              String systemPrompt,
                              String userPrompt,
                              ChatCompletionCreateParams params,
                              String blankCode,
                              String blankMsg) {
    int maxAttempts = Math.max(1, this.maxAttempts); // 1 + retries

    String retryReason = null;
    // We keep the original userPrompt and append retry feedback only on retries.
    String baseUserPrompt = userPrompt == null ? "" : userPrompt;

     for (int attempt = 0; attempt < maxAttempts; attempt++) {
       try {
        String effectiveUserPrompt = baseUserPrompt;
        boolean shouldAppend = attempt > 0 && retryReason != null && !retryReason.trim().isEmpty();
        if (shouldAppend) {
          effectiveUserPrompt = appendRetryReason(baseUserPrompt, retryReason);
        }

        ChatCompletionCreateParams effectiveParams = rebuildWithUserPrompt(params, effectiveUserPrompt);

        ChatCompletion cc = client.chat().completions().create(effectiveParams);
        String out = extractTextFromChatCompletion(cc);

         long latencyMs = (System.nanoTime() - startNano) / 1_000_000;

         Integer inTok = null;
         Integer outTok = null;
         try {
           if (cc.usage().isPresent()) {
             inTok = (int) cc.usage().get().promptTokens();
             outTok = (int) cc.usage().get().completionTokens();
           }
         } catch (Exception ex) {
           log.warn("Failed to parse OpenAI usage tokens antId={} roomId={} model={}", ant.id(), roomId, model(), ex);
         }

         if (isBlank(out)) {
           // Capture something actionable for the next retry.
           String finishReason = null;
           try {
             if (cc.choices() != null && !cc.choices().isEmpty() && cc.choices().get(0) != null) {
               finishReason = String.valueOf(cc.choices().get(0).finishReason());
             }
           } catch (Exception ignore) {
             // ignore
           }
           retryReason = "blank_content" + (finishReason == null ? "" : ("; finishReason=" + finishReason))
               + (outTok == null ? "" : ("; outTok=" + outTok));

           // Log the most useful metadata we can without dumping full prompts.
           try {
             int choices = cc.choices() == null ? 0 : cc.choices().size();
             // reuse finishReason if we already computed it
             if (cc.choices() != null && !cc.choices().isEmpty() && cc.choices().get(0) != null) {
               try {
                 if (finishReason == null) finishReason = String.valueOf(cc.choices().get(0).finishReason());
               } catch (Exception ignore) {
                 // ignore
               }
             }
             log.warn("OpenAI blank content antId={} roomId={} model={} op={} choices={} finishReason={} inTok={} outTok={}",
                 ant.id(), roomId, model(), operation, choices, finishReason, inTok, outTok);
           } catch (Exception ignore) {
             // ignore
           }
           logFailure(log, ant, roomId, model(), operation, latencyMs, blankCode, blankMsg, attempt + 1, maxAttempts);
           throw new IllegalStateException("blank response");
         }

         // --- Prompt/response transcript logging (opt-in) ---
         if (transcriptLogger != null && transcriptLogger.enabled()) {
           transcriptLogger.logPromptAndResponse(
               ant,
               roomId,
               model(),
               operation,
               systemPrompt,
               effectiveUserPrompt,
               out,
               latencyMs,
               inTok,
               outTok
           );
         }

         logSuccess(log, ant, roomId, model(), operation, latencyMs, inTok, outTok, attempt + 1, maxAttempts);
         return out.trim();

       } catch (UnauthorizedException e) {
         long latencyMs = (System.nanoTime() - startNano) / 1_000_000;
         logFailure(log, ant, roomId, model(), operation, latencyMs, e.getClass().getSimpleName(), "auth failed", attempt + 1, maxAttempts);
         throw e;

       } catch (RateLimitException | OpenAIIoException | OpenAIRetryableException e) {
         retryReason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : (": " + e.getMessage()));
         long latencyMs = (System.nanoTime() - startNano) / 1_000_000;
         logFailure(log, ant, roomId, model(), operation, latencyMs, e.getClass().getSimpleName(), e.getMessage(), attempt + 1, maxAttempts);
         if (attempt == maxAttempts - 1) throw e;
         RetryUtil.sleepBackoff(attempt, 250, 2_000);

       } catch (Exception e) {
         retryReason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : (": " + e.getMessage()));
         long latencyMs = (System.nanoTime() - startNano) / 1_000_000;
         logFailure(log, ant, roomId, model(), operation, latencyMs, e.getClass().getSimpleName(), e.getMessage(), attempt + 1, maxAttempts);
         if (attempt == maxAttempts - 1) throw new RuntimeException(e);
         RetryUtil.sleepBackoff(attempt, 250, 2_000);
       }
     }

     throw new IllegalStateException("unreachable");
   }

  private static String appendRetryReason(String prompt, String reason) {
    String p = prompt == null ? "" : prompt;
    String r = reason == null ? "" : reason.trim();
    if (r.isEmpty()) return p;
    return p
        + "\n\n---\n"
        + "Last Response Rejected with error: " + r + "\n"
        + "Please retry, correcting the issue.\n";
  }

  private static ChatCompletionCreateParams rebuildWithUserPrompt(ChatCompletionCreateParams original, String userPrompt) {
    // Rebuild from the original but with the new user message content.
    // We purposely keep the model/temperature/maxCompletionTokens the same.
    ChatCompletionCreateParams.Builder b = ChatCompletionCreateParams.builder();
    try {
      b.model(original.model());
    } catch (Exception ignore) {
      // ignore
    }
    try {
      b.temperature(original.temperature());
    } catch (Exception ignore) {
      // ignore
    }
    try {
      b.maxCompletionTokens(original.maxCompletionTokens());
    } catch (Exception ignore) {
      // ignore
    }

    // Preserve system message if present.
    try {
      if (original.messages() != null && !original.messages().isEmpty()) {
        for (Object m : original.messages()) {
          // We only know how to set system + user in this runner; keep system if we can detect it.
          if (m instanceof ChatCompletionSystemMessageParam) {
            b.addMessage((ChatCompletionSystemMessageParam) m);
          }
        }
      }
    } catch (Exception ignore) {
      // ignore
    }

    // Always add the (possibly augmented) user message.
    b.addMessage(ChatCompletionUserMessageParam.builder().content(userPrompt == null ? "" : userPrompt).build());

    return b.build();
  }

  /**
   * Best-effort extractor for OpenAI Chat Completions output.
   *
   * Why: Some models/SDK versions can return output in a structured form rather than
   * a simple message.content string.
   *
   * This method intentionally uses reflection for the "structured" path so we don't
   * couple to unstable SDK model classes.
   */
  private static String extractTextFromChatCompletion(ChatCompletion cc) {
    if (cc == null || cc.choices() == null || cc.choices().isEmpty()) return null;

    var first = cc.choices().get(0);
    if (first == null || first.message() == null) return null;

    // 1) Normal path: message.content (string)
    try {
      if (first.message().content() != null && first.message().content().isPresent()) {
        String s = first.message().content().get();
        if (!isBlank(s)) return s;
      }
    } catch (Exception ignore) {
      // ignore
    }

    return null;
  }

  private static String stringifyStructuredValue(Object val) {
    if (val == null) return null;
    // OpenAI SDK uses Optional sometimes.
    if (val instanceof java.util.Optional<?> opt) {
      return stringifyStructuredValue(opt.orElse(null));
    }
    if (val instanceof java.lang.CharSequence cs) {
      String s = cs.toString();
      return isBlank(s) ? null : s;
    }
    if (val instanceof java.lang.Iterable<?> it) {
      StringBuilder sb = new StringBuilder();
      for (Object p : it) {
        if (p == null) continue;
        String s = p.toString();
        if (isBlank(s)) continue;
        if (sb.length() > 0) sb.append("\n");
        sb.append(s);
      }
      String joined = sb.toString();
      return isBlank(joined) ? null : joined;
    }
    // Last resort
    String s = val.toString();
    return isBlank(s) ? null : s;
  }
}
