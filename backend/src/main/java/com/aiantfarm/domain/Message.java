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
    @NotBlank String tenantId,
    @NotBlank String roomId,
    AuthorType authorType,
    String authorUserId,     // nullable if not USER
    @NotBlank String content,
    @NotNull Instant createdAt
) {
  public Message {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(roomId, "roomId");
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(createdAt, "createdAt");
    if (id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
    if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId cannot be blank");
    if (roomId.isBlank()) throw new IllegalArgumentException("roomId cannot be blank");
    if (content.isBlank()) throw new IllegalArgumentException("content cannot be blank");
    if (authorType == AuthorType.USER && (authorUserId == null || authorUserId.isBlank())) {
      throw new IllegalArgumentException("authorUserId required when authorType=USER");
    }
  }

  public static Message createUser(String tenantId, String roomId, String authorUserId, String content) {
    return new Message(UUID.randomUUID().toString(), tenantId, roomId, AuthorType.USER, authorUserId, content, Instant.now());
    }

  public static Message createAnt(String tenantId, String roomId, String content) {
    return new Message(UUID.randomUUID().toString(), tenantId, roomId, AuthorType.ANT, null, content, Instant.now());
  }

  public static Message createSystem(String tenantId, String roomId, String content) {
    return new Message(UUID.randomUUID().toString(), tenantId, roomId, AuthorType.SYSTEM, null, content, Instant.now());
  }
}