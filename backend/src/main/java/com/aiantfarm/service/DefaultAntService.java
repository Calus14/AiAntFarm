package com.aiantfarm.service;

import com.aiantfarm.api.RoomController;
import com.aiantfarm.api.dto.*;
import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import com.aiantfarm.domain.AntRoomAssignment;
import com.aiantfarm.domain.AntRun;
import com.aiantfarm.domain.Message;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.repository.*;
import com.aiantfarm.service.ant.AntScheduler;
import com.aiantfarm.service.ant.IAntModelRunner;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DefaultAntService implements IAntService {

  private final AntRepository antRepository;
  private final AntRoomAssignmentRepository assignmentRepository;
  private final AntRunRepository antRunRepository;
  private final RoomRepository roomRepository;
  private final MessageRepository messageRepository;
  private final AntScheduler antScheduler;

  public DefaultAntService(
      AntRepository antRepository,
      AntRoomAssignmentRepository assignmentRepository,
      AntRunRepository antRunRepository,
      RoomRepository roomRepository,
      MessageRepository messageRepository,
      AntScheduler antScheduler
  ) {
    this.antRepository = antRepository;
    this.assignmentRepository = assignmentRepository;
    this.antRunRepository = antRunRepository;
    this.roomRepository = roomRepository;
    this.messageRepository = messageRepository;
    this.antScheduler = antScheduler;
  }

  @PostConstruct
  void warmStartAntSchedules() {
    // !!! IMPORTANT SINGLE-POD WARNING (do not delete):
    // This is an in-memory scheduler bootstrap. On app restart we reschedule ants from Dynamo.
    // If you run multiple backend instances, EACH instance will reschedule ants and you will get
    // duplicate runs/messages.
    //
    // When we scale horizontally, we must replace this with a distributed scheduler (e.g., SQS).

    try {
      List<Ant> ants = antRepository.listAll();
      int scheduled = 0;

      for (Ant ant : ants) {
        if (ant == null) continue;
        if (!ant.enabled()) continue;
        if (assignmentRepository.listByAnt(ant.id()).isEmpty()) continue;

        ensureScheduledIfAssigned(ant);
        scheduled++;
      }

      log.info("Warm-start scheduled ants={} (scanned={})", scheduled, ants.size());
    } catch (Exception e) {
      log.error("Warm-start scheduling failed", e);
    }
  }

  @Override
  public AntDto createAnt(String ownerUserId, CreateAntRequest req) {
    if (req == null) throw new IllegalArgumentException("request required");

    int interval = req.getIntervalSeconds() == null ? 60 : req.getIntervalSeconds();
    boolean enabled = req.getEnabled() != null && req.getEnabled();
    boolean replyEvenIfNoNew = req.getReplyEvenIfNoNew() != null && req.getReplyEvenIfNoNew();
    AiModel model = req.getModel() == null ? AiModel.MOCK : req.getModel();

    Ant ant = Ant.create(ownerUserId, req.getName(), model, req.getPersonalityPrompt(), interval, enabled, replyEvenIfNoNew);
    antRepository.create(ant);

    if (ant.enabled()) {
      ensureScheduledIfAssigned(ant);
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

    Integer intervalSeconds = req != null ? req.getIntervalSeconds() : null;
    if (intervalSeconds != null && intervalSeconds < 5) {
      throw new IllegalArgumentException("intervalSeconds must be >= 5");
    }

    Ant updated = ant.withUpdated(
        req == null ? null : req.getName(),
        req == null ? null : req.getModel(),
        req == null ? null : req.getPersonalityPrompt(),
        intervalSeconds,
        req == null ? null : req.getEnabled(),
        req == null ? null : req.getReplyEvenIfNoNew()
    );

    antRepository.update(updated);

    if (updated.enabled()) {
      ensureScheduledIfAssigned(updated);
    } else {
      antScheduler.cancel(updated.id());
    }

    return toDto(updated);
  }

  @Override
  public void assignToRoom(String ownerUserId, String antId, AssignAntToRoomRequest req) {
    Ant ant = requireOwnedAnt(ownerUserId, antId);

    if (req == null || req.roomId() == null || req.roomId().isBlank()) {
      throw new IllegalArgumentException("roomId required");
    }

    if (roomRepository.findById(req.roomId()).isEmpty()) {
      throw new ResourceNotFoundException("room not found");
    }

    Optional<AntRoomAssignment> existing = assignmentRepository.find(antId, req.roomId());
    if (existing.isPresent()) {
      return;
    }

    assignmentRepository.assign(AntRoomAssignment.create(antId, req.roomId()));

    if (ant.enabled()) {
      ensureScheduledIfAssigned(ant);
    }
  }

  @Override
  public void unassignFromRoom(String ownerUserId, String antId, String roomId) {
    requireOwnedAnt(ownerUserId, antId);
    assignmentRepository.unassign(antId, roomId);

    if (assignmentRepository.listByAnt(antId).isEmpty()) {
      antScheduler.cancel(antId);
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

    List<AntRoomAssignmentDto> items = assignmentRepository.listByRoom(roomId).stream()
        .map(this::toAssignmentDto)
        .toList();

    return new ListResponse<>(items);
  }

  // --- scheduling ---

  private void ensureScheduledIfAssigned(Ant ant) {
    antScheduler.scheduleOrReschedule(ant, () -> runAntTick(ant.id()));
  }

  private void runAntTick(String antId) {
    try {
      Ant ant = antRepository.findById(antId).orElse(null);
      if (ant == null || !ant.enabled()) {
        antScheduler.cancel(antId);
        return;
      }

      List<AntRoomAssignment> assignments = assignmentRepository.listByAnt(antId);
      if (assignments.isEmpty()) {
        antScheduler.cancel(antId);
        return; 
      }

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
      var page = messageRepository.listByRoom(roomId, 1, null);
      String latestMessageId = page.items().isEmpty() ? null : page.items().get(0).id();
      boolean roomChanged = latestMessageId != null && !latestMessageId.equals(assignment.lastSeenMessageId());

      if (!ant.replyEvenIfNoNew() && !roomChanged) {
        AntRun finished = run.succeeded("Skipped: no new messages in room.");
        antRunRepository.update(finished);
        assignmentRepository.update(assignment.withLastSeen(assignment.lastSeenMessageId(), Instant.now()));
        return;
      }

      IAntModelRunner runner = antScheduler.getRunner(ant.model());
      String content = runner.generateMessage(ant, roomId);
      if (content == null || content.isBlank()) {
        throw new IllegalStateException("Model runner returned blank content model=" + ant.model());
      }

      Message msg = Message.createAntMsg(roomId, ant.id(), content);
      messageRepository.create(msg);
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
        a.model(),
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
