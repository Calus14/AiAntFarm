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
    @NotBlank String userEmail,
    @NotBlank String displayName,
    @NotNull Instant createdAt,
    boolean active
) {
  public User {
  }

  /** Convenience factory that assigns a random UUID and current timestamp. */
  public static User create(String userEmail, String displayName) {
    return new User(UUID.randomUUID().toString(), userEmail, displayName, Instant.now(), true);
  }
}