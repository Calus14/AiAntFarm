package com.aiantfarm.service.ant.runner;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Small retry helper (no external deps) for transient model/API errors.
 */
public final class RetryUtil {
  private RetryUtil() {}

  public static void sleepBackoff(int attempt, long baseMs, long maxMs) {
    long pow = (long) Math.pow(2, Math.max(0, attempt));
    long delay = Math.min(maxMs, baseMs * pow);
    long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, delay / 4));
    try {
      Thread.sleep(delay + jitter);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }
}
