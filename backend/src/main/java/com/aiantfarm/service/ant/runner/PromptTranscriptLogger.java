package com.aiantfarm.service.ant.runner;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;

/**
 * Writes full prompt/response transcripts to a local file.
 *
 * !!! IMPORTANT SAFETY/PRIVACY WARNING (do not delete):
 * This is intended for local debugging and will log FULL user content, role prompts, and model outputs.
 * That may include PII, secrets pasted by users, or policy-sensitive text.
 *
 * Do NOT enable in production unless you have:
 * - explicit user consent / clear privacy policy
 * - retention policy + data deletion
 * - access controls + encryption-at-rest
 * - redaction strategy
 */
@Component
@Slf4j
public class PromptTranscriptLogger {

  private final boolean enabled;
  private final Path filePath;

  public PromptTranscriptLogger(
      @Value("${antfarm.ai.transcripts.enabled:false}") boolean enabled,
      @Value("${antfarm.ai.transcripts.file:logs/ai-transcripts.ndjson}") String file
  ) {
    this.enabled = enabled;
    this.filePath = Path.of(file);

    if (this.enabled) {
      try {
        Path parent = filePath.getParent();
        if (parent != null) Files.createDirectories(parent);
      } catch (Exception e) {
        log.warn("Failed to create transcript log directory file={}", filePath, e);
      }

      log.warn("AI transcript logging ENABLED. file={}", filePath);
    }
  }

  public boolean enabled() {
    return enabled;
  }

  public void  logPromptAndResponse(
      Ant ant,
      String roomId,
      AiModel model,
      String operation,
      String systemPrompt,
      String userPrompt,
      String assistantResponse,
      Long latencyMs,
      Integer inputTokens,
      Integer outputTokens
  ) {
    if (!enabled) return;

    try {
      String jsonLine = toNdjson(
          ant,
          roomId,
          model,
          operation,
          systemPrompt,
          userPrompt,
          assistantResponse,
          latencyMs,
          inputTokens,
          outputTokens
      );

      appendLine(jsonLine);
    } catch (Exception e) {
      // Never fail the model call just because transcript logging failed.
      log.warn("Failed to write AI transcript antId={} roomId={} model={} op={}",
          ant != null ? ant.id() : null, roomId, model, operation, e);
    }
  }

  private void appendLine(String line) throws IOException {
    Objects.requireNonNull(line, "line");
    try (BufferedWriter w = Files.newBufferedWriter(
        filePath,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND
    )) {
      w.write(line);
      w.newLine();
    }
  }

  private static String toNdjson(
      Ant ant,
      String roomId,
      AiModel model,
      String operation,
      String systemPrompt,
      String userPrompt,
      String assistantResponse,
      Long latencyMs,
      Integer inputTokens,
      Integer outputTokens
  ) {
    // Minimal JSON escaping (no new deps). This is not a general-purpose serializer.
    return "{" +
        "\"ts\":\"" + esc(Instant.now().toString()) + "\"," +
        "\"antId\":\"" + esc(ant != null ? ant.id() : "") + "\"," +
        "\"antName\":\"" + esc(ant != null ? ant.name() : "") + "\"," +
        "\"roomId\":\"" + esc(roomId) + "\"," +
        "\"model\":\"" + esc(model != null ? model.name() : "") + "\"," +
        "\"operation\":\"" + esc(operation) + "\"," +
        "\"latencyMs\":" + (latencyMs == null ? "null" : latencyMs) + "," +
        "\"inputTokens\":" + (inputTokens == null ? "null" : inputTokens) + "," +
        "\"outputTokens\":" + (outputTokens == null ? "null" : outputTokens) + "," +
        "\"systemPrompt\":\"" + esc(systemPrompt) + "\"," +
        "\"userPrompt\":\"" + esc(userPrompt) + "\"," +
        "\"assistantResponse\":\"" + esc(assistantResponse) + "\"" +
        "}";
  }

  private static String esc(String s) {
    if (s == null) return "";
    // Escape backslash, quotes, and newlines for single-line JSON.
    return s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }
}

