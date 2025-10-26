package com.aiantfarm.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model for a chat room.
 */
public record Room(
    @NotBlank String id,
    @NotBlank String tenantId,
    @NotBlank String name,
    String createdByUserId,      // nullable for system-created rooms
    @NotNull Instant createdAt
) {
  public Room {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(createdAt, "createdAt");
    if (id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
    if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId cannot be blank");
    if (name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
  }

  public static Room create(String tenantId, String name, String createdByUserId) {
    return new Room(UUID.randomUUID().toString(), tenantId, name, createdByUserId, Instant.now());
  }
}