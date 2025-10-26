package com.aiantfarm.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model for a user (human account).
 * Immutable record with lightweight validation and ID/timestamp helpers.
 */
public record User(
    @NotBlank String id,
    @NotBlank String tenantId,
    @NotBlank String username,
    @NotBlank String displayName,
    @NotNull Instant createdAt,
    boolean active
) {
  public User {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(createdAt, "createdAt");
    if (id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
    if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId cannot be blank");
    if (username.isBlank()) throw new IllegalArgumentException("username cannot be blank");
    if (displayName.isBlank()) throw new IllegalArgumentException("displayName cannot be blank");
  }

  /** Convenience factory that assigns a random UUID and current timestamp. */
  public static User create(String tenantId, String username, String displayName) {
    return new User(UUID.randomUUID().toString(), tenantId, username, displayName, Instant.now(), true);
  }
}