package com.aiantfarm.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * User-owned AI agent.
 *
 * NOTE: Ants are owned per user (ownerUserId). They can be assigned to N rooms via AntRoomAssignment.
 */
public record Ant(
    String id,
    String ownerUserId,
    String name,
    AiModel model,
    String personalityPrompt,
    int intervalSeconds,
    boolean enabled,
    boolean replyEvenIfNoNew,
    Instant createdAt,
    Instant updatedAt
) {

  public static Ant create(
      String ownerUserId,
      String name,
      AiModel model,
      String personalityPrompt,
      int intervalSeconds,
      boolean enabled,
      boolean replyEvenIfNoNew
  ) {
    Objects.requireNonNull(ownerUserId, "ownerUserId");
    if (ownerUserId.isBlank()) throw new IllegalArgumentException("ownerUserId required");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
    if (intervalSeconds < 5) throw new IllegalArgumentException("intervalSeconds must be >= 5");

    AiModel safeModel = model == null ? AiModel.MOCK : model;

    Instant now = Instant.now();
    return new Ant(
        UUID.randomUUID().toString(),
        ownerUserId,
        name.trim(),
        safeModel,
        personalityPrompt == null ? "" : personalityPrompt,
        intervalSeconds,
        enabled,
        replyEvenIfNoNew,
        now,
        now
    );
  }

  /**
   * Convenience overload for existing callers; defaults model to MOCK.
   */
  public static Ant create(
      String ownerUserId,
      String name,
      String personalityPrompt,
      int intervalSeconds,
      boolean enabled,
      boolean replyEvenIfNoNew
  ) {
    return create(ownerUserId, name, AiModel.MOCK, personalityPrompt, intervalSeconds, enabled, replyEvenIfNoNew);
  }

  public Ant withUpdated(
      String name,
      AiModel model,
      String personalityPrompt,
      Integer intervalSeconds,
      Boolean enabled,
      Boolean replyEvenIfNoNew
  ) {
    Instant now = Instant.now();
    return new Ant(
        this.id,
        this.ownerUserId,
        name == null ? this.name : name.trim(),
        model == null ? this.model : model,
        personalityPrompt == null ? this.personalityPrompt : personalityPrompt,
        intervalSeconds == null ? this.intervalSeconds : intervalSeconds,
        enabled == null ? this.enabled : enabled,
        replyEvenIfNoNew == null ? this.replyEvenIfNoNew : replyEvenIfNoNew,
        this.createdAt,
        now
    );
  }

  /**
   * Back-compat overload for existing callers.
   */
  public Ant withUpdated(String name, String personalityPrompt, Integer intervalSeconds, Boolean enabled, Boolean replyEvenIfNoNew) {
    return withUpdated(name, null, personalityPrompt, intervalSeconds, enabled, replyEvenIfNoNew);
  }
}
