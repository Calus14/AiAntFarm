package com.aiantfarm.service.ant;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import com.aiantfarm.repository.AntRepository;
import com.aiantfarm.repository.AntRoomAssignmentRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Centralized scheduler for Ant execution.
 *
 * This is an in-memory scheduler (single-instance).
 *
 * !!! IMPORTANT SINGLE-POD WARNING (do not delete):
 * This scheduler assumes exactly ONE backend instance is running.
 * If you run multiple pods/instances, each instance will schedule the same ants and you will get
 * duplicate runs/messages.
 *
 * When you need horizontal scaling, replace this with a distributed mechanism (e.g., SQS + workers)
 * or a leader-election based scheduler.
 */
@Service
@Slf4j
public class AntScheduler {

  private final ScheduledExecutorService scheduler;
  private final ExecutorService workerPool;

  private final Map<String, ScheduledFuture<?>> timersByAntId = new ConcurrentHashMap<>();
  private final Map<AiModel, IAntModelRunner> runners;

  public AntScheduler(
      List<IAntModelRunner> runners,
      AntRepository antRepository,
      AntRoomAssignmentRepository assignmentRepository,
      @Value("${antfarm.ants.schedulerThreads:1}") int schedulerThreads,
      @Value("${antfarm.ants.workerThreads:4}") int workerThreads,
      @Value("${antfarm.ants.workerQueueSize:200}") int workerQueueSize
  ) {
    if (schedulerThreads < 1) schedulerThreads = 1;
    if (workerThreads < 1) workerThreads = 1;
    if (workerQueueSize < 10) workerQueueSize = 10;

    this.scheduler = Executors.newScheduledThreadPool(schedulerThreads, r -> {
      Thread t = new Thread(r, "ant-scheduler");
      t.setDaemon(true);
      return t;
    });

    // Bounded queue so we don't OOM if a ton of ants fire at once.
    BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(workerQueueSize);
    this.workerPool = new ThreadPoolExecutor(
        workerThreads,
        workerThreads,
        0L,
        TimeUnit.MILLISECONDS,
        queue,
        r -> {
          Thread t = new Thread(r, "ant-worker");
          t.setDaemon(true);
          return t;
        },
        new ThreadPoolExecutor.AbortPolicy()
    );

    EnumMap<AiModel, IAntModelRunner> map = new EnumMap<>(AiModel.class);
    for (IAntModelRunner runner : runners) {
      map.put(runner.model(), runner);
    }
    this.runners = Map.copyOf(map);

    log.info("AntScheduler initialized schedulerThreads={} workerThreads={} workerQueueSize={} runners={}",
        schedulerThreads, workerThreads, workerQueueSize, this.runners.keySet());
  }

  /**
   * Ensure an Ant is scheduled at the given interval. If already scheduled, reschedules it.
   */
  public void scheduleOrReschedule(Ant ant, Runnable tick) {
    Objects.requireNonNull(ant, "scheduleOrReschedule::ant");
    Objects.requireNonNull(tick, "scheduleOrReschedule::tick");

    cancel(ant.id());

    // Must be at least 30 seconds,
    long intervalMs = Math.max(ant.intervalSeconds(), 30) * 1000L;
    ScheduledFuture<?> repeatingFuture = scheduler.scheduleAtFixedRate(() -> {
      try {
        // offload tick to worker pool (so scheduler threads stay responsive)
        workerPool.submit(tick);
      } catch (RejectedExecutionException rex) {
        log.warn("Ant tick rejected by worker queue antId={} model={}", ant.id(), ant.model());
      }
    }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

    timersByAntId.put(ant.id(), repeatingFuture);
    log.info("Scheduled antId={} model={} intervalSeconds={}", ant.id(), ant.model(), ant.intervalSeconds());
  }

  public void cancel(String antId) {
    ScheduledFuture<?> existing = timersByAntId.remove(antId);
    if (existing != null) {
      existing.cancel(false);
    }
  }

  public IAntModelRunner getRunner(AiModel model) {
    AiModel m = model == null ? AiModel.MOCK : model;
    IAntModelRunner runner = runners.get(m);
    if (runner == null) {
      runner = runners.get(AiModel.MOCK);
    }
    return runner;
  }

  @PreDestroy
  public void shutdown() {
    try {
      scheduler.shutdownNow();
    } catch (Exception ignored) {}
    try {
      workerPool.shutdownNow();
    } catch (Exception ignored) {}
  }
}
