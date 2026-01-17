package com.aiantfarm.service.ant.runner;

import com.aiantfarm.domain.AiModel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-local collector for one ant scheduler tick.
 *
 * Goal:
 * - Collect per-model-call metrics (latency/tokens/estimated cost/retries)
 * - Log a single summary line once per tick
 *
 * This is intentionally ThreadLocal (per your request). If we later need cross-thread correlation,
 * we can extend this to an MDC/runId based approach.
 */
public final class AntRunMetrics {

  private AntRunMetrics() {}

  private static final ThreadLocal<Collector> TL = new ThreadLocal<>();

  public static void start(String antId) {
    TL.set(new Collector(antId));
  }

  public static Collector current() {
    return TL.get();
  }

  public static void clear() {
    TL.remove();
  }

  public static void record(
      String operation,
      AiModel model,
      long latencyMs,
      Integer inputTokens,
      Integer outputTokens,
      BigDecimal estUsd,
      int attempt,
      int maxAttempts,
      boolean success,
      String errorClass
  ) {
    Collector c = TL.get();
    if (c == null) return;
    c.events.add(new Event(operation, model, latencyMs, inputTokens, outputTokens, estUsd, attempt, maxAttempts, success, errorClass));
  }

  public static Summary snapshotSummary() {
    Collector c = TL.get();
    if (c == null) return new Summary(0, 0, 0, BigDecimal.ZERO, Collections.emptyList());
    return c.toSummary();
  }

  public record Event(
      String operation,
      AiModel model,
      long latencyMs,
      Integer inputTokens,
      Integer outputTokens,
      BigDecimal estUsd,
      int attempt,
      int maxAttempts,
      boolean success,
      String errorClass
  ) {}

  public record Summary(
      int requests,
      int successes,
      int failures,
      BigDecimal estUsd,
      List<Event> events
  ) {}

  public static final class Collector {
    final String antId;
    final List<Event> events = new ArrayList<>();

    Collector(String antId) {
      this.antId = antId;
    }

    Summary toSummary() {
      int req = events.size();
      int ok = 0;
      int fail = 0;
      BigDecimal usd = BigDecimal.ZERO;

      for (Event e : events) {
        if (e.success) ok++; else fail++;
        if (e.estUsd != null) usd = usd.add(e.estUsd);
      }

      return new Summary(req, ok, fail, usd, List.copyOf(events));
    }
  }
}

