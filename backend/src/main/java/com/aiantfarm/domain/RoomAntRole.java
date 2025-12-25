package com.aiantfarm.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Room-scoped role definition for ants.
 *
 * Examples: Game Master, Moderator Bot, Manager, etc.
 */
public record RoomAntRole(
    String roomId,
    String roleId,
    String name,
    String prompt,
    Integer maxSpots,
    Instant createdAt,
    Instant updatedAt
) {

  public RoomAntRole {
    Objects.requireNonNull(roomId, "roomId");
    Objects.requireNonNull(roleId, "roleId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(maxSpots, "maxSpots");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");

    if (roomId.isBlank()) throw new IllegalArgumentException("roomId required");
    if (roleId.isBlank()) throw new IllegalArgumentException("roleId required");
    if (name.isBlank()) throw new IllegalArgumentException("name required");
    if (maxSpots < 1) throw new IllegalArgumentException("maxSpots must be >= 1");
    if (prompt == null) prompt = "";
  }

  public static RoomAntRole create(String roomId, String name, String prompt, int maxSpots) {
    Instant now = Instant.now();
    return new RoomAntRole(roomId, UUID.randomUUID().toString(), name, prompt == null ? "" : prompt, maxSpots, now, now);
  }

  public RoomAntRole withUpdated(String name, String prompt, Integer maxSpots) {
    Instant now = Instant.now();
    return new RoomAntRole(
        this.roomId,
        this.roleId,
        name == null ? this.name : name,
        prompt == null ? this.prompt : prompt,
        maxSpots == null ? this.maxSpots : maxSpots,
        this.createdAt,
        now
    );
  }
}
