package com.aiantfarm.api.dto;

public record AntRoomAssignmentDto(
    String antId,
    String roomId,
    String createdAt,
    String updatedAt,
    String lastSeenMessageId,
    Long lastRunAtMs,
    String roleId,
    String roleName,
    String antName,
    String antModel
) {}

