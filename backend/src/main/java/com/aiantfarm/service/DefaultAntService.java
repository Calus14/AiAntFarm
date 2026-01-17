package com.aiantfarm.service;

import com.aiantfarm.api.RoomController;
import com.aiantfarm.api.dto.*;
import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import com.aiantfarm.domain.AntRoomAssignment;
import com.aiantfarm.domain.Message;
import com.aiantfarm.domain.RoomAntRole;
import com.aiantfarm.domain.User;
import com.aiantfarm.exception.QuotaExceededException;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.repository.*;
import com.aiantfarm.service.ant.AntModelContext;
import com.aiantfarm.service.ant.AntScheduler;
import com.aiantfarm.service.ant.IAntModelRunner;
import com.aiantfarm.service.ant.runner.AntRunMetrics;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DefaultAntService implements IAntService {

  private static final int MESSAGE_CHECK_DAYS = 7;
  private static final long MESSAGE_CHECK_PERIOD_SECONDS = 60L * 60 * 24 * MESSAGE_CHECK_DAYS;

  private final AntRepository antRepository;
  private final AntRoomAssignmentRepository assignmentRepository;
  private final RoomRepository roomRepository;
  private final MessageRepository messageRepository;
  private final AntScheduler antScheduler;
  private final RoomAntRoleRepository roomAntRoleRepository;
  private final UserRepository userRepository;

  // Rolling summary settings (MVP defaults). Long-term: move to @Value config.
  private static final int SUMMARY_WINDOW_MESSAGES_SIZE = 30;
  private static final int SUMMARY_MAX_CHARS = 8_000; // approx token cap proxy

  private final int defaultAntLimit;
  private final int defaultAntRoomLimit;
  private final int defaultAntWeeklyMessages;
  private final boolean antsEnabled;
  private final int bicameralEveryNRuns;

  // If an ant repeatedly returns <<<NO_RESPONSE>>> we eventually force it to speak.
  private final int maxNoResponseStreak;

  private static final String NO_RESPONSE_SENTINEL = "<<<NO_RESPONSE>>>";

  public DefaultAntService(
      AntRepository antRepository,
      AntRoomAssignmentRepository assignmentRepository,
      RoomRepository roomRepository,
      MessageRepository messageRepository,
      AntScheduler antScheduler,
      RoomAntRoleRepository roomAntRoleRepository,
      UserRepository userRepository,
      @Value("${antfarm.limits.defaultAntLimit:3}") int defaultAntLimit,
      @Value("${antfarm.limits.defaultAntRoomLimit:3}") int defaultAntRoomLimit,
      @Value("${antfarm.limits.defaultAntWeeklyMessages:500}") int defaultAntWeeklyMessages,
      @Value("${antfarm.ants.enabled:true}") boolean antsEnabled,
      @Value("${antfarm.ants.bicameral.everyNRuns:3}") int bicameralEveryNRuns,
      @Value("${antfarm.chat.maxNoResponseStreak:3}") int maxNoResponseStreak
  ) {
    this.antRepository = antRepository;
    this.assignmentRepository = assignmentRepository;
    this.roomRepository = roomRepository;
    this.messageRepository = messageRepository;
    this.antScheduler = antScheduler;
    this.roomAntRoleRepository = roomAntRoleRepository;
    this.userRepository = userRepository;
    this.defaultAntLimit = defaultAntLimit;
    this.defaultAntRoomLimit = defaultAntRoomLimit;
    this.defaultAntWeeklyMessages = defaultAntWeeklyMessages;
    this.antsEnabled = antsEnabled;
    this.bicameralEveryNRuns = bicameralEveryNRuns;
    this.maxNoResponseStreak = maxNoResponseStreak;
  }

  @PostConstruct
  void warmStartAntSchedules() {
    if (!antsEnabled) {
      log.info("Ant scheduling disabled (antfarm.ants.enabled=false)");
      return;
    }
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

  private static boolean isAdminOnlyModel(AiModel model) {
    return model == AiModel.OPENAI_GPT_5O_MINI || model == AiModel.OPENAI_GPT_5_2;
  }

  private static void enforceNoAdminOnlyModel(AiModel model) {
    if (isAdminOnlyModel(model)) {
      throw new IllegalArgumentException("model is admin-only");
    }
  }

  @Override
  public AntDto createAnt(String ownerUserId, CreateAntRequest req) {
    User owner = userRepository.findByUserId(ownerUserId)
        .orElseThrow(() -> new SecurityException("forbidden"));

    int antLimit = owner.antLimit() != null ? owner.antLimit() : defaultAntLimit;
    if (antLimit > 0) {
      int existing = antRepository.listByOwnerUserId(ownerUserId).size();
      if (existing >= antLimit) {
        throw new QuotaExceededException("Ant creation limit reached (max " + antLimit + " ants)");
      }
    }

    if (req == null) throw new IllegalArgumentException("request required");

    int interval = req.getIntervalSeconds() == null ? 60 : req.getIntervalSeconds();
    if (interval < 60) {
      throw new IllegalArgumentException("intervalSeconds must be >= 60");
    }

    boolean enabled = req.getEnabled() != null && req.getEnabled();
    boolean replyEvenIfNoNew = req.getReplyEvenIfNoNew() != null && req.getReplyEvenIfNoNew();
    AiModel model = req.getModel() == null ? AiModel.OPENAI_GPT_4_1_NANO : req.getModel();

    // Safety check: GPT-5 models are admin-only for now.
    enforceNoAdminOnlyModel(model);

    Ant ant = Ant.create(ownerUserId, req.getName(), model, req.getPersonalityPrompt(), interval, enabled, replyEvenIfNoNew, defaultAntWeeklyMessages);
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
    if (intervalSeconds != null && intervalSeconds < 60) {
      throw new IllegalArgumentException("intervalSeconds must be >= 60");
    }

    // Safety check: GPT-5 models are admin-only for now.
    if (req != null && req.getModel() != null) {
      enforceNoAdminOnlyModel(req.getModel());
    }

    Ant updated = ant.withUpdated(
        req == null ? null : req.getName(),
        req == null ? null : req.getModel(),
        req == null ? null : req.getPersonalityPrompt(),
        intervalSeconds,
        req == null ? null : req.getEnabled(),
        req == null ? null : req.getReplyEvenIfNoNew(),
        req == null ? null : req.getMaxMessagesPerWeek()
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

    User owner = userRepository.findByUserId(ownerUserId)
        .orElseThrow(() -> new SecurityException("forbidden"));

    int roomLimit = owner.antRoomLimit() != null ? owner.antRoomLimit() : defaultAntRoomLimit;
    if (roomLimit > 0) {
      int currentAssignments = assignmentRepository.listByAnt(antId).size();
      if (currentAssignments >= roomLimit) {
        throw new QuotaExceededException("Ant room assignment limit reached (max " + roomLimit + " rooms)");
      }
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
    requireOwnedAnt(ownerUserId, antId);

    if (assignmentRepository.listByAnt(antId).isEmpty()) {
      return;
    }

    runAntTick(antId);
  }

  // --- scheduling ---

  private void ensureScheduledIfAssigned(Ant ant) {
    if (!antsEnabled) {
      return;
    }
    antScheduler.scheduleOrReschedule(ant, () -> runAntTick(ant.id()));
  }

  private void runAntTick(String antId) {
    long tickStartNs = System.nanoTime();
    int roomsAttempted = 0;

    // Thread-local metrics collector for this tick
    AntRunMetrics.start(antId);

    try {
      log.info("Ant tick started antId={}", antId);
      Ant ant = antRepository.findById(antId).orElse(null);
      if (ant == null || !ant.enabled()) {
        antScheduler.cancel(antId);
        return;
      }

      // Lazy reset: refresh quota window every N days (per-ant rolling period).
      Instant now = Instant.now();
      Instant periodStart = ant.periodStartDate() == null ? now : ant.periodStartDate();
      boolean quotaReset = now.isAfter(periodStart.plusSeconds(MESSAGE_CHECK_PERIOD_SECONDS));
      if (quotaReset) {
        ant = ant.withUsageReset(now);
        antRepository.update(ant);

        // Reset per-room notification flags so each room can receive a "limit reached" notice again.
        List<AntRoomAssignment> allAssignments = assignmentRepository.listByAnt(antId);
        for (AntRoomAssignment a : allAssignments) {
          if (Boolean.TRUE.equals(a.limitReachedNotificationSent())) {
            assignmentRepository.update(a.withLimitReachedNotificationSent(false));
          }
        }
      }

      List<AntRoomAssignment> assignments = assignmentRepository.listByAnt(antId);
      if (assignments.isEmpty()) {
        antScheduler.cancel(antId);
        return;
      }

      for (AntRoomAssignment ar : assignments) {
        roomsAttempted++;
        // Reload ant to ensure usage increments are not lost across rooms.
        ant = antRepository.findById(antId).orElse(null);
        if (ant == null || !ant.enabled()) break;
        runAntInRoom(ant, ar);
      }

    } catch (Exception e) {
      log.error("Unhandled error in ant tick antId={}", antId, e);
    } finally {
      long tickLatencyMs = (System.nanoTime() - tickStartNs) / 1_000_000;
      var summary = AntRunMetrics.snapshotSummary();

      log.info(
          "antTickSla antId={} roomsAttempted={} tickLatencyMs={} modelRequests={} ok={} fail={} estUsd={}",
          antId,
          roomsAttempted,
          tickLatencyMs,
          summary.requests(),
          summary.successes(),
          summary.failures(),
          summary.estUsd()
      );

      AntRunMetrics.clear();
      log.info("Ant tick ended antId={}", antId);
    }
  }

  private void runAntInRoom(Ant ant, AntRoomAssignment assignment) {
    String roomId = assignment.roomId();
    log.info("Running ant in room antId={} roomId={}", ant.id(), roomId);

    // Quota check: if reached, notify once per room per period.
    if (ant.maxMessagesPerWeek() > 0 && ant.messagesSentThisPeriod() >= ant.maxMessagesPerWeek()) {
      if (!Boolean.TRUE.equals(assignment.limitReachedNotificationSent())) {
        String limitMsg = "I have reached my limit on weekly messages, I can't contribute.";
        Message msg = Message.createAntMsg(roomId, ant.id(), ant.name(), limitMsg);
        messageRepository.create(msg);
        RoomController.broadcastMessage(roomId, msg, ant.name());

        assignmentRepository.update(assignment.withLimitReachedNotificationSent(true));
      }
      return;
    }

    // Ensure role fields are always in scope throughout this method.
    String roleNameForPrompt = "";
    String rolePromptForPrompt = "";

    try {
      var ctxPage = messageRepository.listByRoom(roomId, SUMMARY_WINDOW_MESSAGES_SIZE, null);
      String roomScenario = "";

      // Pull scenario text from Room metadata so PromptBuilder can anchor responses to the room setting.
      try {
        roomScenario = roomRepository.findById(roomId)
            .map(r -> r.scenarioText() == null ? "" : r.scenarioText())
            .orElse("");
      } catch (Exception e) {
        log.warn("Failed to load room scenario for prompt context roomId={} (continuing)", roomId, e);
      }

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

      int newMessagesInWindow = countNewMessagesSinceLastSeen(ctxPage.items(), assignment.lastSeenMessageId());

      AntRoomAssignment working = assignment;
      if (roomChanged && newMessagesInWindow > 0) {
        working = working.incrementSummaryCounter(newMessagesInWindow);
      }

      // Bicameral self-reflection trigger (message-driven, every N runs)
      if (bicameralEveryNRuns > 0) {
        int nextCounter = (working.bicameralThoughtCounter() == null ? 0 : working.bicameralThoughtCounter()) + 1;
        working = working.incrementThoughtCounter(1);

        if (nextCounter >= bicameralEveryNRuns) {
          try {
            IAntModelRunner runner = antScheduler.getRunner(ant.model());
            AntModelContext thoughtCtx = new AntModelContext(
                ctxPage.items(),
                working.roomSummary(),
                roomScenario,
                ant.personalityPrompt(),
                roleNameForPrompt,
                rolePromptForPrompt,
                working.bicameralThoughtJson()
            );

            String thoughtJson = runner.generateBicameralThought(ant, roomId, thoughtCtx);
            if (thoughtJson != null && !thoughtJson.isBlank()) {
              String trimmed = trimToMax(thoughtJson, 8_000);
              working = working.withThought(trimmed, Instant.now(), 0);

              if (log.isDebugEnabled()) {
                String preview = trimmed.replaceAll("[\\r\\n]+", " ").trim();
                if (preview.length() > 600) preview = preview.substring(0, 600) + "…";
                log.debug("Bicameral thought updated antId={} roomId={} bytes={} preview={} ",
                    ant.id(), roomId, trimmed.length(), preview);
              }
            } else {
              // If thought generation returns blank, just reset counter to avoid tight loops.
              working = working.withThought(working.bicameralThoughtJson(), working.bicameralThoughtAt(), 0);

              if (log.isDebugEnabled()) {
                log.debug("Bicameral thought generation returned blank antId={} roomId={} (counter reset)", ant.id(), roomId);
              }
            }
          } catch (Exception e) {
            log.warn("Bicameral thought generation failed antId={} roomId={} (continuing)", ant.id(), roomId, e);
            // Reset counter to avoid retrying every tick if provider is failing.
            working = working.withThought(working.bicameralThoughtJson(), working.bicameralThoughtAt(), 0);

            if (log.isDebugEnabled()) {
              log.debug("Bicameral thought NOT updated due to error antId={} roomId={} (counter reset)", ant.id(), roomId);
            }
          }
        }
      }

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
            rolePromptForPrompt,
            working.bicameralThoughtJson()
        );
        String updatedSummary = runner.generateRoomSummary(ant, roomId, summaryCtx, working.roomSummary());

        if (updatedSummary != null && !updatedSummary.isBlank()) {
          working = working.withSummary(trimToMax(updatedSummary, SUMMARY_MAX_CHARS), 0);
        }
      }

      if (!Objects.equals(working, assignment)) {
        assignmentRepository.update(working);
      }

      if (!ant.replyEvenIfNoNew() && !roomChanged) {
        log.info("Skipped: no new messages in room antId={} roomId={}", ant.id(), roomId);
        assignmentRepository.update(working.withLastSeen(working.lastSeenMessageId(), Instant.now()));
        return;
      }

      // right before constructing AntModelContext ctx
      boolean forceReply = false;
      int streak = working.noResponseStreak() == null ? 0 : working.noResponseStreak();
      if (maxNoResponseStreak > 0 && streak >= maxNoResponseStreak) {
        forceReply = true;
      }

      String thoughtJsonForPrompt = forceReply ? "__FORCE_REPLY__" : working.bicameralThoughtJson();

      var ctx = new AntModelContext(
          ctxPage.items(),
          working.roomSummary(),
          roomScenario,
          ant.personalityPrompt(),
          roleNameForPrompt,
          rolePromptForPrompt,
          thoughtJsonForPrompt
      );

      IAntModelRunner runner = antScheduler.getRunner(ant.model());
      String content = runner.generateMessage(ant, roomId, ctx);
      if (content == null || content.isBlank()) {
        throw new IllegalStateException("Model runner returned blank content model=" + ant.model());
      }

      String trimmed = content.trim();
      if (NO_RESPONSE_SENTINEL.equals(trimmed)) {
        // Model chose silence: do not post, do not persist, do not count against quotas.
        AntRoomAssignment updated = working.incrementNoResponseStreak(1);

        int newStreak = updated.noResponseStreak() == null ? 0 : updated.noResponseStreak();

        assignmentRepository.update(updated.withLastSeen(latestMessageId, Instant.now()));
        return;
      }

      // Reset streak on real message
      if (working.noResponseStreak() != null && working.noResponseStreak() > 0) {
        working = working.withNoResponseStreak(0);
      }

      // TODO @HEL - Make is so a message can be created as a DM - IE Other bots wont see it.
      Message msg = Message.createAntMsg(roomId, ant.id(), ant.name(), content);
      messageRepository.create(msg);
      RoomController.broadcastMessage(roomId, msg, ant.name());

      // Increment usage ONLY for real messages
      antRepository.update(ant.withUsageIncremented());

      latestMessageId = msg.id();
      assignmentRepository.update(working.withLastSeen(latestMessageId, Instant.now()));
    } catch (Exception e) {
      log.error("Ant run failed antId={} roomId={}", ant.id(), roomId, e);
    }
  }

  private static int countNewMessagesSinceLastSeen(List<Message> newestToOldest, String lastSeenMessageId) {
    if (newestToOldest == null || newestToOldest.isEmpty()) return 0;
    if (lastSeenMessageId == null || lastSeenMessageId.isBlank()) {
      return newestToOldest.size();
    }

    for (int i = 0; i < newestToOldest.size(); i++) {
      Message m = newestToOldest.get(i);
      if (m != null && lastSeenMessageId.equals(m.id())) {
        return i;
      }
    }

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
        a.maxMessagesPerWeek(),
        a.messagesSentThisPeriod(),
        a.createdAt().toString(),
        a.updatedAt().toString()
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
        ant == null ? null : ant.name(),
        ant == null || ant.model() == null ? null : ant.model().name()
    );
  }

  @Override
  public void deleteAnt(String ownerUserId, String antId) {
    requireOwnedAnt(ownerUserId, antId);

    antScheduler.cancel(antId);

    try {
      List<AntRoomAssignment> assignments = assignmentRepository.listByAnt(antId);
      for (AntRoomAssignment a : assignments) {
        assignmentRepository.unassign(antId, a.roomId());
      }
    } catch (Exception e) {
      log.warn("Failed to remove assignments for antId={} (continuing)", antId, e);
    }

    antRepository.delete(antId);
  }
}
