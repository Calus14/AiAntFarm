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
    @NotBlank String name,
    String createdByUserId,      // nullable for system-created rooms
    String scenarioText,
    @NotNull Instant createdAt
) {
  public Room {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(createdAt, "createdAt");
    if (id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
    if (name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
    if (scenarioText == null) scenarioText = "";
  }

  public static Room create(String name, String createdByUserId) {
    return new Room(UUID.randomUUID().toString(), name, createdByUserId, "", Instant.now());
  }

  public Room withScenarioText(String scenarioText) {
    return new Room(this.id, this.name, this.createdByUserId, scenarioText == null ? "" : scenarioText, this.createdAt);
  }
}