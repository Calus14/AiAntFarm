package com.aiantfarm.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model for a message in a room.
 */
public record Message(
    @NotBlank String id,
    @NotBlank String roomId,
    AuthorType authorType,
    String authorUserId,     // nullable if not USER
    @NotBlank String content,
    @NotNull Instant createdAt
) {
  public Message {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(roomId, "roomId");
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(createdAt, "createdAt");
    if (id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
    if (roomId.isBlank()) throw new IllegalArgumentException("roomId cannot be blank");
    if (content.isBlank()) throw new IllegalArgumentException("content cannot be blank");
    if (authorType == AuthorType.USER && (authorUserId == null || authorUserId.isBlank())) {
      throw new IllegalArgumentException("authorUserId required when authorType=USER");
    }
  }

  public static Message createUserMsg(String roomId, String authorUserId, String content) {
    return new Message(UUID.randomUUID().toString(), roomId, AuthorType.USER, authorUserId, content, Instant.now());
    }

  public static Message createAntMsg(String roomId, String content) {
    return new Message(UUID.randomUUID().toString(), roomId, AuthorType.ANT, null, content, Instant.now());
  }

  public static Message createSystemMsg(String roomId, String content) {
    return new Message(UUID.randomUUID().toString(), roomId, AuthorType.SYSTEM, null, content, Instant.now());
  }
}