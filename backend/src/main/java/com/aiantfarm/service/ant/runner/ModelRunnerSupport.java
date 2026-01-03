package com.aiantfarm.service.ant.runner;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Lightweight base for runner implementations.
 */
public abstract class ModelRunnerSupport {

  protected void logSuccess(Logger log,
                            Ant ant,
                            String roomId,
                            AiModel model,
                            long latencyMs,
                            Integer inputTokens,
                            Integer outputTokens) {
    // Back-compat log line used by existing tooling
    log.info("antModel ok antId={} roomId={} model={} latencyMs={} inputTokens={} outputTokens={}",
        ant.id(), roomId, model, latencyMs,
        inputTokens == null ? -1 : inputTokens,
        outputTokens == null ? -1 : outputTokens);

    // New SLA/cost-friendly version (single line, parseable)
    BigDecimal costUsd = estimateUsd(model, inputTokens, outputTokens);
    log.info("antModelSla ok antId={} roomId={} model={} latencyMs={} inputTokens={} outputTokens={} estUsd={}",
        ant.id(), roomId, model, latencyMs,
        inputTokens == null ? -1 : inputTokens,
        outputTokens == null ? -1 : outputTokens,
        costUsd);
  }

  protected void logSuccess(Logger log,
                            Ant ant,
                            String roomId,
                            AiModel model,
                            String operation,
                            long latencyMs,
                            Integer inputTokens,
                            Integer outputTokens,
                            int attempt,
                            int maxAttempts) {
    // Back-compat
    log.info("antModel ok antId={} roomId={} model={} latencyMs={} inputTokens={} outputTokens={}",
        ant.id(), roomId, model, latencyMs,
        inputTokens == null ? -1 : inputTokens,
        outputTokens == null ? -1 : outputTokens);

    BigDecimal costUsd = estimateUsd(model, inputTokens, outputTokens);

    AntRunMetrics.record(
        operation,
        model,
        latencyMs,
        inputTokens,
        outputTokens,
        costUsd,
        attempt,
        maxAttempts,
        true,
        null
    );

    log.info("antModelSla ok antId={} roomId={} model={} op={} latencyMs={} inputTokens={} outputTokens={} estUsd={} attempt={} maxAttempts={}",
        ant.id(), roomId, model, operation, latencyMs,
        inputTokens == null ? -1 : inputTokens,
        outputTokens == null ? -1 : outputTokens,
        costUsd,
        attempt,
        maxAttempts);
  }

  protected void logFailure(Logger log,
                            Ant ant,
                            String roomId,
                            AiModel model,
                            long latencyMs,
                            String errorClass,
                            String message) {
    log.warn("antModel fail antId={} roomId={} model={} latencyMs={} errorClass={} message={}",
        ant.id(), roomId, model, latencyMs, errorClass, message);

    log.warn("antModelSla fail antId={} roomId={} model={} latencyMs={} errorClass={} message={}",
        ant.id(), roomId, model, latencyMs, errorClass, message);
  }

  protected void logFailure(Logger log,
                            Ant ant,
                            String roomId,
                            AiModel model,
                            String operation,
                            long latencyMs,
                            String errorClass,
                            String message,
                            int attempt,
                            int maxAttempts) {
    log.warn("antModel fail antId={} roomId={} model={} latencyMs={} errorClass={} message={}",
        ant.id(), roomId, model, latencyMs, errorClass, message);

    AntRunMetrics.record(
        operation,
        model,
        latencyMs,
        -1,
        -1,
        BigDecimal.ZERO,
        attempt,
        maxAttempts,
        false,
        errorClass
    );

    log.warn("antModelSla fail antId={} roomId={} model={} op={} latencyMs={} errorClass={} message={} attempt={} maxAttempts={}",
        ant.id(), roomId, model, operation, latencyMs, errorClass, message, attempt, maxAttempts);
  }

  /**
   * Estimate USD cost for a request from token usage.
   *
   * IMPORTANT: Prices change. Treat these as a best-effort estimate for observability.
   *
   * Units: $ per 1M tokens.
   */
  protected static BigDecimal estimateUsd(AiModel model, Integer inputTokens, Integer outputTokens) {
    long inTok = inputTokens == null || inputTokens < 0 ? 0 : inputTokens;
    long outTok = outputTokens == null || outputTokens < 0 ? 0 : outputTokens;

    Pricing p = Pricing.forModel(model);
    if (p == null) return BigDecimal.ZERO;

    BigDecimal in = BigDecimal.valueOf(inTok).multiply(p.usdPer1MInput).divide(BigDecimal.valueOf(1_000_000L), 12, RoundingMode.HALF_UP);
    BigDecimal out = BigDecimal.valueOf(outTok).multiply(p.usdPer1MOutput).divide(BigDecimal.valueOf(1_000_000L), 12, RoundingMode.HALF_UP);
    return in.add(out).setScale(8, RoundingMode.HALF_UP);
  }

  protected static class Pricing {
    final BigDecimal usdPer1MInput;
    final BigDecimal usdPer1MOutput;

    Pricing(BigDecimal usdPer1MInput, BigDecimal usdPer1MOutput) {
      this.usdPer1MInput = usdPer1MInput;
      this.usdPer1MOutput = usdPer1MOutput;
    }

    static Pricing forModel(AiModel model) {
      if (model == null) return null;

      // NOTE: These are placeholders until you choose exact pricing.
      // They are used only to produce an *estimate* in logs.
      return switch (model) {
        case OPENAI_GPT_4_1_NANO -> new Pricing(BigDecimal.valueOf(0.15), BigDecimal.valueOf(0.60));
        case OPENAI_GPT_4O_MINI -> new Pricing(BigDecimal.valueOf(0.15), BigDecimal.valueOf(0.60));
        case ANTHROPIC_HAIKU -> new Pricing(BigDecimal.valueOf(0.25), BigDecimal.valueOf(1.25));
      };
    }
  }

  protected static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}
