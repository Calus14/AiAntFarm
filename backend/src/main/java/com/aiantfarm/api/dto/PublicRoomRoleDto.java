package com.aiantfarm.api.dto;

/**
 * Public, read-only view of a room role.
 *
 * IMPORTANT: This intentionally does NOT expose the role prompt.
 */
public record PublicRoomRoleDto(
    String roleId,
    String roomId,
    String name,
    Integer maxSpots
) {}

