package com.aiantfarm.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates average request duration per endpoint over a rolling window of N requests.
 *
 * Behavior:
 * - Measures total time per HTTP request
 * - Buckets by: METHOD + normalized endpoint path (best-effort)
 * - When count reaches N for that endpoint, logs avgMs at INFO and resets counters
 */
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SlaAveragingFilter extends OncePerRequestFilter {

  private final int sampleSize;

  private static final class Acc {
    final AtomicLong count = new AtomicLong(0);
    final AtomicLong totalMs = new AtomicLong(0);
  }

  private final Map<String, Acc> byEndpoint = new ConcurrentHashMap<>();

  public SlaAveragingFilter(@Value("${antfarm.sla.endpointSampleSize:50}") int sampleSize) {
    this.sampleSize = sampleSize <= 0 ? 50 : sampleSize;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    // Avoid skew from health checks and preflight
    if (path == null) return false;
    if (path.startsWith("/actuator")) return true;
    return "OPTIONS".equalsIgnoreCase(request.getMethod());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    long startNs = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long ms = (System.nanoTime() - startNs) / 1_000_000;
      String key = endpointKey(request);

      Acc acc = byEndpoint.computeIfAbsent(key, k -> new Acc());
      long c = acc.count.incrementAndGet();
      long total = acc.totalMs.addAndGet(ms);

      if (c >= sampleSize) {
        double avg = total / (double) c;
        int status = response.getStatus();
        log.info("endpointSlaAvg endpoint={} sampleSize={} avgMs={} lastStatus={}", key, c, String.format("%.2f", avg), status);

        // reset
        acc.count.set(0);
        acc.totalMs.set(0);
      }
    }
  }

  private static String endpointKey(HttpServletRequest req) {
    String method = req.getMethod() == null ? "?" : req.getMethod().toUpperCase();

    // Best-effort normalization: collapse obvious IDs to {id}
    String path = req.getRequestURI();
    if (path == null) path = "";

    path = path
        .replaceAll("/\\d+", "/{id}")
        .replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "/{uuid}");

    return method + " " + path;
  }
}

