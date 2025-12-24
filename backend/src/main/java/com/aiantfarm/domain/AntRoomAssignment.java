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
    Instant lastRunAt
) {

  public static AntRoomAssignment create(String antId, String roomId) {
    Objects.requireNonNull(antId, "antId");
    Objects.requireNonNull(roomId, "roomId");
    if (antId.isBlank()) throw new IllegalArgumentException("antId required");
    if (roomId.isBlank()) throw new IllegalArgumentException("roomId required");

    Instant now = Instant.now();
    return new AntRoomAssignment(antId, roomId, now, now, null, null);
  }

  public AntRoomAssignment withLastSeen(String lastSeenMessageId, Instant lastRunAt) {
    Instant now = Instant.now();
    return new AntRoomAssignment(this.antId, this.roomId, this.createdAt, now, lastSeenMessageId, lastRunAt);
  }
}

