package com.aiantfarm.service.ant.runner;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import org.slf4j.Logger;

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
    log.info("antModel ok antId={} roomId={} model={} latencyMs={} inputTokens={} outputTokens={}",
        ant.id(), roomId, model, latencyMs,
        inputTokens == null ? -1 : inputTokens,
        outputTokens == null ? -1 : outputTokens);
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
  }

  protected static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}

