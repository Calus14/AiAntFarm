package com.aiantfarm.api.dto;

/**
 * Public, read-only view of a room.
 *
 * Keep this intentionally limited: no owner/creator identifiers.
 */
public record PublicRoomDto(
    String roomId,
    String name,
    String scenarioText,
    String createdAt
) {}
