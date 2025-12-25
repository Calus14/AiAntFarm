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

    // Rolling summary of the room from this ant's perspective.
    // This is NOT exposed to the UI; it is only used to construct prompts.
    String roomSummary,

    // Counter that we increment as new room messages accumulate.
    // When it crosses a threshold (e.g., >= windowSize) we regenerate roomSummary.
    Integer summaryMsgCounter
) {

  public static AntRoomAssignment create(String antId, String roomId) {
    Objects.requireNonNull(antId, "antId");
    Objects.requireNonNull(roomId, "roomId");
    if (antId.isBlank()) throw new IllegalArgumentException("antId required");
    if (roomId.isBlank()) throw new IllegalArgumentException("roomId required");

    Instant now = Instant.now();
    return new AntRoomAssignment(antId, roomId, now, now, null, null, null, 0);
  }

  public AntRoomAssignment withLastSeen(String lastSeenMessageId, Instant lastRunAt) {
    Instant now = Instant.now();
    return new AntRoomAssignment(this.antId, this.roomId, this.createdAt, now, lastSeenMessageId, lastRunAt,
        this.roomSummary, this.summaryMsgCounter);
  }

  public AntRoomAssignment incrementSummaryCounter(int delta) {
    int current = this.summaryMsgCounter == null ? 0 : this.summaryMsgCounter;
    int next = Math.max(0, current + Math.max(0, delta));
    Instant now = Instant.now();
    return new AntRoomAssignment(this.antId, this.roomId, this.createdAt, now, this.lastSeenMessageId, this.lastRunAt,
        this.roomSummary, next);
  }

  public AntRoomAssignment withSummary(String roomSummary, int resetCounterTo) {
    Instant now = Instant.now();
    return new AntRoomAssignment(this.antId, this.roomId, this.createdAt, now, this.lastSeenMessageId, this.lastRunAt,
        roomSummary, Math.max(0, resetCounterTo));
  }
}
