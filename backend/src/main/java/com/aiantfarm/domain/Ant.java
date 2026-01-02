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
    int maxMessagesPerWeek,
    int messagesSentThisPeriod,
    Instant periodStartDate,
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
      boolean replyEvenIfNoNew,
      Integer maxMessagesPerWeek
  ) {
    Objects.requireNonNull(ownerUserId, "ownerUserId");
    if (ownerUserId.isBlank()) throw new IllegalArgumentException("ownerUserId required");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
    if (intervalSeconds < 60) throw new IllegalArgumentException("intervalSeconds must be >= 60");

    AiModel safeModel = model == null ? AiModel.OPENAI_GPT_4_1_NANO : model;
    int safeMaxMessages = maxMessagesPerWeek == null ? 2 : maxMessagesPerWeek;

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
        safeMaxMessages,
        0,
        now,
        now,
        now
    );
  }

  /**
   * Convenience overload for existing callers; defaults model to OPENAI_GPT_4_1_NANO.
   */
  public static Ant create(
      String ownerUserId,
      String name,
      String personalityPrompt,
      int intervalSeconds,
      boolean enabled,
      boolean replyEvenIfNoNew
  ) {
    return create(ownerUserId, name, AiModel.OPENAI_GPT_4_1_NANO, personalityPrompt, intervalSeconds, enabled, replyEvenIfNoNew, 500);
  }

  public Ant withUpdated(
      String name,
      AiModel model,
      String personalityPrompt,
      Integer intervalSeconds,
      Boolean enabled,
      Boolean replyEvenIfNoNew,
      Integer maxMessagesPerWeek
  ) {
    Instant now = Instant.now();

    int nextInterval = intervalSeconds == null ? this.intervalSeconds : intervalSeconds;
    if (nextInterval < 60) throw new IllegalArgumentException("intervalSeconds must be >= 60");

    return new Ant(
        this.id,
        this.ownerUserId,
        name == null ? this.name : name.trim(),
        model == null ? this.model : model,
        personalityPrompt == null ? this.personalityPrompt : personalityPrompt,
        nextInterval,
        enabled == null ? this.enabled : enabled,
        replyEvenIfNoNew == null ? this.replyEvenIfNoNew : replyEvenIfNoNew,
        maxMessagesPerWeek == null ? this.maxMessagesPerWeek : maxMessagesPerWeek,
        this.messagesSentThisPeriod,
        this.periodStartDate,
        this.createdAt,
        now
    );
  }

  /**
   * Back-compat overload for existing callers.
   */
  public Ant withUpdated(String name, String personalityPrompt, Integer intervalSeconds, Boolean enabled, Boolean replyEvenIfNoNew) {
    return withUpdated(name, null, personalityPrompt, intervalSeconds, enabled, replyEvenIfNoNew, null);
  }

  public Ant withUsageIncremented() {
      return new Ant(
          this.id,
          this.ownerUserId,
          this.name,
          this.model,
          this.personalityPrompt,
          this.intervalSeconds,
          this.enabled,
          this.replyEvenIfNoNew,
          this.maxMessagesPerWeek,
          this.messagesSentThisPeriod + 1,
          this.periodStartDate,
          this.createdAt,
          Instant.now()
      );
  }

  public Ant withUsageReset(Instant newPeriodStart) {
      return new Ant(
          this.id,
          this.ownerUserId,
          this.name,
          this.model,
          this.personalityPrompt,
          this.intervalSeconds,
          this.enabled,
          this.replyEvenIfNoNew,
          this.maxMessagesPerWeek,
          0,
          newPeriodStart,
          this.createdAt,
          Instant.now()
      );
  }
}
