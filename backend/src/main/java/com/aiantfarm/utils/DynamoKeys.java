package com.aiantfarm.utils;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Central place to build DynamoDB single-table PK/SK values.
 *
 * Domain IDs (userId/roomId/messageId) should remain "raw" UUIDs.
 * This class applies the storage prefixes.
 */
public final class DynamoKeys {
  private DynamoKeys() {}

  public static String userPk(String userId) {
    require(userId, "userId");
    return "USER#" + userId;
  }

  public static String userProfileSk(String userId) {
    require(userId, "userId");
    return "PROFILE#" + userId;
  }

  public static String credSk() {
    return "CRED#PRIMARY";
  }

  public static String roomPk(String roomId) {
    require(roomId, "roomId");
    return "ROOM#" + roomId;
  }

  public static String roomMetaSk(String roomId) {
    require(roomId, "roomId");
    return "META#" + roomId;
  }

  public static String roomMemberSk(String userId) {
    require(userId, "userId");
    return "MEMBER#" + userId;
  }

  public static String messageSk(Instant createdAt, String messageId) {
    Objects.requireNonNull(createdAt, "createdAt");
    require(messageId, "messageId");
    return "MSG#" + DateTimeFormatter.ISO_INSTANT.format(createdAt) + "#" + messageId;
  }

  private static void require(String s, String name) {
    if (s == null || s.isBlank()) throw new IllegalArgumentException(name + " must be set");
  }
}

