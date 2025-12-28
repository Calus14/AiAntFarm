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

  // --- Ants ---

  public static String antPk(String antId) {
    require(antId, "antId");
    return "ANT#" + antId;
  }

  public static String antMetaSk(String antId) {
    require(antId, "antId");
    return "META#" + antId;
  }

  public static String antOwnerGsiPk(String ownerUserId) {
    require(ownerUserId, "ownerUserId");
    return "OWNER#" + ownerUserId;
  }

  public static String antRoomSk(String roomId) {
    require(roomId, "roomId");
    return "ROOM#" + roomId;
  }

  public static String roomAntSk(String antId) {
    require(antId, "antId");
    return "ANT#" + antId;
  }

  public static String antRunSk(Instant startedAt, String runId) {
    Objects.requireNonNull(startedAt, "startedAt");
    require(runId, "runId");
    return "RUN#" + DateTimeFormatter.ISO_INSTANT.format(startedAt) + "#" + runId;
  }

  public static String roomAntRoleSk(String roleId) {
    require(roleId, "roleId");
    return "ANTROLE#" + roleId;
  }

  private static void require(String s, String name) {
    if (s == null || s.isBlank()) throw new IllegalArgumentException(name + " must be set");
  }
}
