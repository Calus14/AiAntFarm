package com.aiantfarm.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model for membership of a user in a room (single-tenant).
 */
public record RoomMembership(
    @NotBlank String id,
    @NotBlank String roomId,
    @NotBlank String userId,
    @NotNull RoomRole role,
    @NotNull Instant joinedAt
) {
  public RoomMembership {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(roomId, "roomId");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(joinedAt, "joinedAt");
    if (id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
    if (roomId.isBlank()) throw new IllegalArgumentException("roomId cannot be blank");
    if (userId.isBlank()) throw new IllegalArgumentException("userId cannot be blank");
  }

  public static RoomMembership createOwner(String roomId, String userId) {
    return new RoomMembership(UUID.randomUUID().toString(), roomId, userId, RoomRole.OWNER, Instant.now());
  }

  public static RoomMembership create(String roomId, String userId, RoomRole role) {
    return new RoomMembership(UUID.randomUUID().toString(), roomId, userId, role, Instant.now());
  }
}