package com.aiantfarm.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Many-to-many join between Ant and Room.
 *
 * This is where we keep per-room state for an Ant, rather than bloating the Ant item.
 */
public record AntRoomAssignment(
    String antId,
    String roomId,
    Instant createdAt,
    Instant updatedAt,
    String lastSeenMessageId,
    Instant lastRunAt,

    // If true, we already posted the "weekly quota reached" message to this room for the current quota period.
    Boolean limitReachedNotificationSent,

    // Ant role for this specific room. Only ants can have roles.
    String roleId,
    String roleName,

    // Rolling summary of the room from this ant's perspective.
    // This is NOT exposed to the UI; it is only used to construct prompts.
    String roomSummary,

    // Counter that we increment as new room messages accumulate.
    // When it crosses a threshold (e.g., >= windowSize) we regenerate roomSummary.
    Integer summaryMsgCounter,

    // --- Bicameral self-reflection (internal-only; persisted for continuity) ---
    String bicameralThoughtJson,
    Instant bicameralThoughtAt,
    Integer bicameralThoughtCounter
) {

  public static AntRoomAssignment create(String antId, String roomId) {
    Objects.requireNonNull(antId, "antId");
    Objects.requireNonNull(roomId, "roomId");
    if (antId.isBlank()) throw new IllegalArgumentException("antId required");
    if (roomId.isBlank()) throw new IllegalArgumentException("roomId required");

    Instant now = Instant.now();
    return new AntRoomAssignment(antId, roomId, now, now, null, null, false, null, null, null, 0,
        "", null, 0);
  }

  public AntRoomAssignment withLastSeen(String lastSeenMessageId, Instant lastRunAt) {
    Instant now = Instant.now();
    return new AntRoomAssignment(this.antId, this.roomId, this.createdAt, now, lastSeenMessageId, lastRunAt,
        this.limitReachedNotificationSent,
        this.roleId, this.roleName,
        this.roomSummary, this.summaryMsgCounter,
        this.bicameralThoughtJson, this.bicameralThoughtAt, this.bicameralThoughtCounter);
  }

  public AntRoomAssignment withRole(String roleId, String roleName) {
    Instant now = Instant.now();
    return new AntRoomAssignment(this.antId, this.roomId, this.createdAt, now, this.lastSeenMessageId, this.lastRunAt,
        this.limitReachedNotificationSent,
        roleId, roleName,
        this.roomSummary, this.summaryMsgCounter,
        this.bicameralThoughtJson, this.bicameralThoughtAt, this.bicameralThoughtCounter);
  }

  public AntRoomAssignment withLimitReachedNotificationSent(boolean sent) {
    Instant now = Instant.now();
    return new AntRoomAssignment(this.antId, this.roomId, this.createdAt, now, this.lastSeenMessageId, this.lastRunAt,
        sent,
        this.roleId, this.roleName,
        this.roomSummary, this.summaryMsgCounter,
        this.bicameralThoughtJson, this.bicameralThoughtAt, this.bicameralThoughtCounter);
  }

  public AntRoomAssignment incrementSummaryCounter(int delta) {
    int current = this.summaryMsgCounter == null ? 0 : this.summaryMsgCounter;
    int next = Math.max(0, current + Math.max(0, delta));
    Instant now = Instant.now();
    return new AntRoomAssignment(this.antId, this.roomId, this.createdAt, now, this.lastSeenMessageId, this.lastRunAt,
        this.limitReachedNotificationSent,
        this.roleId, this.roleName,
        this.roomSummary, next,
        this.bicameralThoughtJson, this.bicameralThoughtAt, this.bicameralThoughtCounter);
  }

  public AntRoomAssignment withSummary(String roomSummary, int resetCounterTo) {
    Instant now = Instant.now();
    return new AntRoomAssignment(this.antId, this.roomId, this.createdAt, now, this.lastSeenMessageId, this.lastRunAt,
        this.limitReachedNotificationSent,
        this.roleId, this.roleName,
        roomSummary, Math.max(0, resetCounterTo),
        this.bicameralThoughtJson, this.bicameralThoughtAt, this.bicameralThoughtCounter);
  }

  public AntRoomAssignment incrementThoughtCounter(int delta) {
    int current = this.bicameralThoughtCounter == null ? 0 : this.bicameralThoughtCounter;
    int next = Math.max(0, current + Math.max(0, delta));
    Instant now = Instant.now();
    return new AntRoomAssignment(this.antId, this.roomId, this.createdAt, now, this.lastSeenMessageId, this.lastRunAt,
        this.limitReachedNotificationSent,
        this.roleId, this.roleName,
        this.roomSummary, this.summaryMsgCounter,
        this.bicameralThoughtJson, this.bicameralThoughtAt, next);
  }

  public AntRoomAssignment withThought(String thoughtJson, Instant thoughtAt, int resetCounterTo) {
    Instant now = Instant.now();
    return new AntRoomAssignment(this.antId, this.roomId, this.createdAt, now, this.lastSeenMessageId, this.lastRunAt,
        this.limitReachedNotificationSent,
        this.roleId, this.roleName,
        this.roomSummary, this.summaryMsgCounter,
        thoughtJson == null ? "" : thoughtJson,
        thoughtAt,
        Math.max(0, resetCounterTo));
  }
}
