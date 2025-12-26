package com.aiantfarm.service;

import com.aiantfarm.api.RoomController;
import com.aiantfarm.api.dto.*;
import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import com.aiantfarm.domain.AntRoomAssignment;
import com.aiantfarm.domain.AntRun;
import com.aiantfarm.domain.Message;
import com.aiantfarm.domain.RoomAntRole;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.repository.*;
import com.aiantfarm.service.ant.AntModelContext;
import com.aiantfarm.service.ant.AntScheduler;
import com.aiantfarm.service.ant.IAntModelRunner;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
  private final RoomAntRoleRepository roomAntRoleRepository;

  // Rolling summary settings (MVP defaults). Long-term: move to @Value config.
  private static final int SUMMARY_WINDOW_MESSAGES_SIZE = 30;
  private static final int SUMMARY_MAX_CHARS = 8_000; // approx token cap proxy

  public DefaultAntService(
      AntRepository antRepository,
      AntRoomAssignmentRepository assignmentRepository,
      AntRunRepository antRunRepository,
      RoomRepository roomRepository,
      MessageRepository messageRepository,
      AntScheduler antScheduler,
      RoomAntRoleRepository roomAntRoleRepository
  ) {
    this.antRepository = antRepository;
    this.assignmentRepository = assignmentRepository;
    this.antRunRepository = antRunRepository;
    this.roomRepository = roomRepository;
    this.messageRepository = messageRepository;
    this.antScheduler = antScheduler;
    this.roomAntRoleRepository = roomAntRoleRepository;
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

    List<AntRoomAssignment> assignments = assignmentRepository.listByRoom(roomId);
    
    List<AntRoomAssignmentDto> items = assignments.stream()
        .map(a -> {
            Ant ant = antRepository.findById(a.antId()).orElse(null);
            return toAssignmentDto(a, ant);
        })
        .toList();

    return new ListResponse<>(items);
  }

  @Override
  public void runNow(String ownerUserId, String antId) {
    // Validate ownership and existence
    Ant ant = requireOwnedAnt(ownerUserId, antId);

    // If there are no assignments, nothing to run.
    if (assignmentRepository.listByAnt(antId).isEmpty()) {
      return;
    }

    // Run immediately (synchronously) using the same logic as scheduled ticks.
    // This has the same SINGLE-POD semantics as the in-memory scheduler.
    runAntTick(antId);
  }

  // --- scheduling ---

  private void ensureScheduledIfAssigned(Ant ant) {
    antScheduler.scheduleOrReschedule(ant, () -> runAntTick(ant.id()));
  }

  private void runAntTick(String antId) {
    try {
      log.info("Ant tick started antId={}", antId);
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
    log.info("Ant tick ended antId={}", antId);
  }

  private void runAntInRoom(Ant ant, AntRoomAssignment assignment) {
    String roomId = assignment.roomId();
    log.info("Running ant in room antId={} roomId={}", ant.id(), roomId);

    AntRun run = AntRun.started(ant.id(), ant.ownerUserId(), roomId);
    antRunRepository.create(run);

    // Ensure role fields are always in scope throughout this method.
    String roleNameForPrompt = "";
    String rolePromptForPrompt = "";

    try {
      // Load the message context window once per run.
      // Repository returns newest -> oldest.
      var ctxPage = messageRepository.listByRoom(roomId, SUMMARY_WINDOW_MESSAGES_SIZE, null);

      // Scenario is not implemented yet. For now it's blank, but the plumbing is here.
      String roomScenario = "";

      // If a role is assigned, load the role prompt so the model can actually follow it.
      roleNameForPrompt = assignment.roleName() == null ? "" : assignment.roleName();
      rolePromptForPrompt = "";
      if (assignment.roleId() != null && !assignment.roleId().isBlank()) {
        try {
          RoomAntRole role = roomAntRoleRepository.find(roomId, assignment.roleId()).orElse(null);
          if (role != null) {
            roleNameForPrompt = role.name() == null ? roleNameForPrompt : role.name();
            rolePromptForPrompt = role.prompt() == null ? "" : role.prompt();
          }
        } catch (Exception e) {
          log.warn("Failed to load role for ant prompt antId={} roomId={} roleId={} (continuing)",
              ant.id(), roomId, assignment.roleId(), e);
        }
      }

      String latestMessageId = ctxPage.items().isEmpty() ? null : ctxPage.items().get(0).id();
      boolean roomChanged = latestMessageId != null && !latestMessageId.equals(assignment.lastSeenMessageId());

      // --- Rolling summary counter update ---
      // We do NOT increment by 1 per run.
      // An ant might run every 10 minutes and multiple messages could be posted between runs.
      // We increment by the number of messages that are newer than the assignment's lastSeenMessageId,
      // based on what we can observe in the current context window. If lastSeenMessageId is not present
      // in the window, we conservatively assume at least (window size) new messages.
      int newMessagesInWindow = countNewMessagesSinceLastSeen(ctxPage.items(), assignment.lastSeenMessageId());

      AntRoomAssignment working = assignment;
      if (roomChanged && newMessagesInWindow > 0) {
        working = working.incrementSummaryCounter(newMessagesInWindow);
      }

      // If we've accumulated enough new messages OR there's no summary yet, regenerate rolling summary.
      int counter = working.summaryMsgCounter() == null ? 0 : working.summaryMsgCounter();
      boolean summaryMissing = working.roomSummary() == null || working.roomSummary().isBlank();
      boolean shouldRegenSummary = roomChanged && (summaryMissing || counter >= SUMMARY_WINDOW_MESSAGES_SIZE);

      if (shouldRegenSummary) {
        IAntModelRunner runner = antScheduler.getRunner(ant.model());

        AntModelContext summaryCtx = new AntModelContext(
            ctxPage.items(),
            working.roomSummary(),
            roomScenario,
            ant.personalityPrompt(),
            roleNameForPrompt,
            rolePromptForPrompt
        );
        String updatedSummary = runner.generateRoomSummary(ant, roomId, summaryCtx, working.roomSummary());

        if (updatedSummary != null && !updatedSummary.isBlank()) {
          // Reset counter after successful regen.
          working = working.withSummary(trimToMax(updatedSummary, SUMMARY_MAX_CHARS), 0);
        }
      }

      // Persist any summary changes before potential model call.
      if (!Objects.equals(working, assignment)) {
        assignmentRepository.update(working);
      }

      if (!ant.replyEvenIfNoNew() && !roomChanged) {
        AntRun finished = run.succeeded("Skipped: no new messages in room.");
        antRunRepository.update(finished);
        assignmentRepository.update(working.withLastSeen(working.lastSeenMessageId(), Instant.now()));
        return;
      }

      // Build context including the rolling summary and (future) scenario.
      var ctx = new AntModelContext(
          ctxPage.items(),
          working.roomSummary(),
          roomScenario,
          ant.personalityPrompt(),
          roleNameForPrompt,
          rolePromptForPrompt
      );

      IAntModelRunner runner = antScheduler.getRunner(ant.model());
      String content = runner.generateMessage(ant, roomId, ctx);
      if (content == null || content.isBlank()) {
        throw new IllegalStateException("Model runner returned blank content model=" + ant.model());
      }

      Message msg = Message.createAntMsg(roomId, ant.id(), ant.name(), content);
      messageRepository.create(msg);
      RoomController.broadcastMessage(roomId, msg, ant.name());

      // The last message in the room is the one we just posted
      latestMessageId = msg.id();

      AntRun finished = run.succeeded("Posted message to room. roomChanged=" + roomChanged);
      antRunRepository.update(finished);
      assignmentRepository.update(working.withLastSeen(latestMessageId, Instant.now()));
    } catch (Exception e) {
      AntRun failed = run.failed(null, e.getMessage());
      antRunRepository.update(failed);
      log.error("Ant run failed antId={} roomId={}", ant.id(), roomId, e);
    }
  }

  private static int countNewMessagesSinceLastSeen(List<Message> newestToOldest, String lastSeenMessageId) {
    if (newestToOldest == null || newestToOldest.isEmpty()) return 0;
    if (lastSeenMessageId == null || lastSeenMessageId.isBlank()) {
      // First run: treat the whole window as new.
      return newestToOldest.size();
    }

    for (int i = 0; i < newestToOldest.size(); i++) {
      Message m = newestToOldest.get(i);
      if (m != null && lastSeenMessageId.equals(m.id())) {
        // Messages are newest->oldest, so items [0..i-1] are newer than last seen.
        return i;
      }
    }

    // lastSeenMessageId not in current window: at least window-size messages are newer.
    return newestToOldest.size();
  }

  private static String trimToMax(String s, int maxChars) {
    if (s == null) return "";
    if (maxChars <= 0) return s;
    String t = s.trim();
    if (t.length() <= maxChars) return t;
    return t.substring(t.length() - maxChars);
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

  private AntRoomAssignmentDto toAssignmentDto(AntRoomAssignment a, Ant ant) {
    Long lastRunAtMs = a.lastRunAt() != null ? a.lastRunAt().toEpochMilli() : null;
    return new AntRoomAssignmentDto(
        a.antId(),
        a.roomId(),
        a.createdAt() != null ? a.createdAt().toString() : null,
        a.updatedAt() != null ? a.updatedAt().toString() : null,
        a.lastSeenMessageId(),
        lastRunAtMs,
        a.roleId(),
        a.roleName(),
        ant != null ? ant.name() : null,
        ant != null ? ant.model().name() : null
    );
  }


  @Override
  public void deleteAnt(String ownerUserId, String antId) {
    Ant ant = requireOwnedAnt(ownerUserId, antId);

    // Cancel scheduler
    antScheduler.cancel(antId);

    // Remove assignments
    try {
      List<AntRoomAssignment> assignments = assignmentRepository.listByAnt(antId);
      for (AntRoomAssignment a : assignments) {
        assignmentRepository.unassign(antId, a.roomId());
      }
    } catch (Exception e) {
      log.warn("Failed to remove assignments for antId={} (continuing)", antId, e);
    }

    // Delete runs? not required now; AntRun can remain for audit, but we delete the ant meta
    antRepository.delete(antId);
  }

  @Override
  public void clearRuns(String ownerUserId, String antId) {
    requireOwnedAnt(ownerUserId, antId);
    antRunRepository.deleteAllByAnt(antId);
  }
}
