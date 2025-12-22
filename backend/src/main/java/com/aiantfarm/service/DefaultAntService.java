package com.aiantfarm.service;

import com.aiantfarm.api.RoomController;
import com.aiantfarm.api.dto.AntDetailDto;
import com.aiantfarm.api.dto.AntDto;
import com.aiantfarm.api.dto.AntRunDto;
import com.aiantfarm.api.dto.AntRoomAssignmentDto;
import com.aiantfarm.api.dto.AssignAntToRoomRequest;
import com.aiantfarm.api.dto.CreateAntRequest;
import com.aiantfarm.api.dto.ListResponse;
import com.aiantfarm.api.dto.UpdateAntRequest;
import com.aiantfarm.domain.Ant;
import com.aiantfarm.domain.AntRoomAssignment;
import com.aiantfarm.domain.AntRun;
import com.aiantfarm.domain.Message;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.repository.AntRepository;
import com.aiantfarm.repository.AntRoomAssignmentRepository;
import com.aiantfarm.repository.AntRunRepository;
import com.aiantfarm.repository.MessageRepository;
import com.aiantfarm.repository.RoomRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DefaultAntService implements IAntService {

  private final AntRepository antRepository;
  private final AntRoomAssignmentRepository assignmentRepository;
  private final AntRunRepository antRunRepository;
  private final RoomRepository roomRepository;
  private final MessageRepository messageRepository;

  private final Map<String, ScheduledFuture<?>> timersByAntId = new ConcurrentHashMap<>();

  private final ScheduledExecutorService antScheduler = Executors.newScheduledThreadPool(2, r -> {
    Thread t = new Thread(r, "ant-scheduler");
    t.setDaemon(true);
    return t;
  });

  public DefaultAntService(
      AntRepository antRepository,
      AntRoomAssignmentRepository assignmentRepository,
      AntRunRepository antRunRepository,
      RoomRepository roomRepository,
      MessageRepository messageRepository
  ) {
    this.antRepository = antRepository;
    this.assignmentRepository = assignmentRepository;
    this.antRunRepository = antRunRepository;
    this.roomRepository = roomRepository;
    this.messageRepository = messageRepository;
  }

  @Override
  public AntDto createAnt(String ownerUserId, CreateAntRequest req) {
    if (req == null) throw new IllegalArgumentException("request required");

    int interval = req.intervalSeconds() == null ? 60 : req.intervalSeconds();
    boolean enabled = req.enabled() != null && req.enabled();
    boolean replyEvenIfNoNew = req.replyEvenIfNoNew() != null && req.replyEvenIfNoNew();

    Ant ant = Ant.create(ownerUserId, req.name(), req.personalityPrompt(), interval, enabled, replyEvenIfNoNew);
    antRepository.create(ant);

    if (ant.enabled()) {
      ensureTimerRunning(ant);
    }

    return toDto(ant);
  }

  @Override
  public ListResponse<AntDto> listMyAnts(String ownerUserId) {
    List<AntDto> items = antRepository.listByOwnerUserId(ownerUserId).stream()
        .map(this::toDto)
        .collect(Collectors.toList());
    return new ListResponse<>(items);
  }

  @Override
  public AntDetailDto getAnt(String ownerUserId, String antId) {
    Ant ant = requireOwnedAnt(ownerUserId, antId);
    List<String> rooms = assignmentRepository.listByAnt(antId)
        .stream().map(AntRoomAssignment::roomId).toList();
    return new AntDetailDto(toDto(ant), rooms);
  }

  @Override
  public AntDto updateAnt(String ownerUserId, String antId, UpdateAntRequest req) {
    Ant ant = requireOwnedAnt(ownerUserId, antId);

    Integer intervalSeconds = req != null ? req.intervalSeconds() : null;
    if (intervalSeconds != null && intervalSeconds < 5) {
      throw new IllegalArgumentException("intervalSeconds must be >= 5");
    }

    Ant updated = ant.withUpdated(
        req == null ? null : req.name(),
        req == null ? null : req.personalityPrompt(),
        intervalSeconds,
        req == null ? null : req.enabled(),
        req == null ? null : req.replyEvenIfNoNew()
    );

    antRepository.update(updated);

    // reschedule timer based on new settings
    if (updated.enabled()) {
      ensureTimerRunning(updated);
    } else {
      cancelTimer(updated.id());
    }

    return toDto(updated);
  }

  @Override
  public void assignToRoom(String ownerUserId, String antId, AssignAntToRoomRequest req) {
    Ant ant = requireOwnedAnt(ownerUserId, antId);

    if (req == null || req.roomId() == null || req.roomId().isBlank()) {
      throw new IllegalArgumentException("roomId required");
    }

    // Ensure room exists (no room membership checks yet; consistent with current backend)
    if (roomRepository.findById(req.roomId()).isEmpty()) {
      throw new ResourceNotFoundException("room not found");
    }

    // idempotent-ish: if exists, do nothing
    Optional<AntRoomAssignment> existing = assignmentRepository.find(antId, req.roomId());
    if (existing.isPresent()) {
      return;
    }

    assignmentRepository.assign(AntRoomAssignment.create(antId, req.roomId()));

    if (ant.enabled()) {
      ensureTimerRunning(ant);
    }
  }

  @Override
  public void unassignFromRoom(String ownerUserId, String antId, String roomId) {
    requireOwnedAnt(ownerUserId, antId);
    assignmentRepository.unassign(antId, roomId);

    // If no rooms remain, stop the timer.
    if (assignmentRepository.listByAnt(antId).isEmpty()) {
      cancelTimer(antId);
    }
  }

  @Override
  public ListResponse<AntRunDto> listRuns(String ownerUserId, String antId, Integer limit) {
    requireOwnedAnt(ownerUserId, antId);
    int n = limit == null ? 50 : limit;
    List<AntRunDto> items = antRunRepository.listByAnt(antId, n)
        .stream().map(this::toRunDto).toList();
    return new ListResponse<>(items);
  }

  @Override
  public ListResponse<AntRoomAssignmentDto> listAntsInRoom(String roomId) {
    if (roomId == null || roomId.isBlank()) {
      return new ListResponse<>(List.of());
    }

    // We return assignment state. If later you want richer UI (ant name), we can add an expanded DTO.
    List<AntRoomAssignmentDto> items = assignmentRepository.listByRoom(roomId).stream()
        .map(this::toAssignmentDto)
        .toList();

    return new ListResponse<>(items);
  }

  // --- scheduler ---

  private void ensureTimerRunning(Ant ant) {
    // Only start if the ant has at least one room assignment.
    if (assignmentRepository.listByAnt(ant.id()).isEmpty()) {
      return;
    }

    timersByAntId.compute(ant.id(), (antId, existing) -> {
      if (existing != null && !existing.isCancelled() && !existing.isDone()) {
        // If interval unchanged, keep; otherwise reschedule.
        // We can't easily check the existing schedule rate, so keep it simple: cancel and reschedule on update.
        existing.cancel(false);
      }

      long intervalMs = Math.max(ant.intervalSeconds(), 5) * 1000L;
      return antScheduler.scheduleAtFixedRate(
          () -> runAntTick(ant.id()),
          intervalMs,
          intervalMs,
          TimeUnit.MILLISECONDS
      );
    });

    log.info("Ant timer running antId={} intervalSeconds={}", ant.id(), ant.intervalSeconds());
  }

  private void cancelTimer(String antId) {
    ScheduledFuture<?> f = timersByAntId.remove(antId);
    if (f != null) {
      f.cancel(false);
      log.info("Ant timer cancelled antId={}", antId);
    }
  }

  private void runAntTick(String antId) {
    try {
      Ant ant = antRepository.findById(antId).orElse(null);
      if (ant == null) {
        cancelTimer(antId);
        return;
      }
      if (!ant.enabled()) {
        cancelTimer(antId);
        return;
      }

      List<AntRoomAssignment> assignments = assignmentRepository.listByAnt(antId);
      if (assignments.isEmpty()) {
        cancelTimer(antId);
        return;
      }

      // Run per room. (In future we might want per-room rate limiting / concurrency caps.)
      for (AntRoomAssignment ar : assignments) {
        runAntInRoom(ant, ar);
      }

    } catch (Exception e) {
      log.error("Unhandled error in ant tick antId={}", antId, e);
    }
  }

  private void runAntInRoom(Ant ant, AntRoomAssignment assignment) {
    String roomId = assignment.roomId();

    AntRun run = AntRun.started(ant.id(), ant.ownerUserId(), roomId);
    antRunRepository.create(run);

    try {
      // Determine whether room changed.
      // We consider the room "changed" if the latest message id differs from lastSeenMessageId.
      var page = messageRepository.listByRoom(roomId, 1, null);
      String latestMessageId = page.items().isEmpty() ? null : page.items().get(0).id();
      boolean roomChanged = latestMessageId != null && !latestMessageId.equals(assignment.lastSeenMessageId());

      if (!ant.replyEvenIfNoNew() && !roomChanged) {
        AntRun finished = run.succeeded("Skipped: no new messages in room.");
        antRunRepository.update(finished);
        assignmentRepository.update(assignment.withLastSeen(assignment.lastSeenMessageId(), Instant.now()));
        return;
      }

      // --- AI call placeholder ---
      // For now, generate a deterministic message so you can validate end-to-end scheduling + SSE.
      // Later this will call OpenAI via a provider abstraction.
      //
      // !!! SAFETY/ABUSE NOTE (do not delete):
      // When you plug in real models, you must treat user-controlled room content and persona prompts
      // as untrusted input. Prompt injection and spam are real. Add rate limits, moderation hooks,
      // and make sure you never execute tools/actions unless explicitly allowed.
      String content = "[" + ant.name() + "] " + "I’m alive. Latest message=" + (latestMessageId == null ? "<none>" : latestMessageId);

      Message msg = Message.createAntMsg(roomId, ant.id(), content);
      messageRepository.create(msg);

      // Broadcast via SSE (reuse RoomController static emitter map)
      RoomController.broadcastMessage(roomId, msg);

      AntRun finished = run.succeeded("Posted message to room. roomChanged=" + roomChanged);
      antRunRepository.update(finished);

      assignmentRepository.update(assignment.withLastSeen(latestMessageId, Instant.now()));

    } catch (Exception e) {
      AntRun failed = run.failed(null, e.getMessage());
      antRunRepository.update(failed);
      log.error("Ant run failed antId={} roomId={}", ant.id(), roomId, e);
    }
  }

  // --- helpers ---

  private Ant requireOwnedAnt(String ownerUserId, String antId) {
    Ant ant = antRepository.findById(antId).orElseThrow(() -> new ResourceNotFoundException("ant not found"));
    if (!ownerUserId.equals(ant.ownerUserId())) {
      throw new SecurityException("forbidden");
    }
    return ant;
  }

  private AntDto toDto(Ant a) {
    return new AntDto(
        a.id(),
        a.ownerUserId(),
        a.name(),
        a.personalityPrompt(),
        a.intervalSeconds(),
        a.enabled(),
        a.replyEvenIfNoNew(),
        a.createdAt().toString(),
        a.updatedAt().toString()
    );
  }

  private AntRunDto toRunDto(AntRun r) {
    Long startedMs = r.startedAt() != null ? r.startedAt().toEpochMilli() : null;
    Long finishedMs = r.finishedAt() != null ? r.finishedAt().toEpochMilli() : null;
    return new AntRunDto(
        r.id(),
        r.antId(),
        r.roomId(),
        r.status().name(),
        startedMs,
        finishedMs,
        r.antNotes(),
        r.error()
    );
  }

  private AntRoomAssignmentDto toAssignmentDto(AntRoomAssignment a) {
    Long lastRunAtMs = a.lastRunAt() != null ? a.lastRunAt().toEpochMilli() : null;
    return new AntRoomAssignmentDto(
        a.antId(),
        a.roomId(),
        a.createdAt() != null ? a.createdAt().toString() : null,
        a.updatedAt() != null ? a.updatedAt().toString() : null,
        a.lastSeenMessageId(),
        lastRunAtMs
    );
  }
}
