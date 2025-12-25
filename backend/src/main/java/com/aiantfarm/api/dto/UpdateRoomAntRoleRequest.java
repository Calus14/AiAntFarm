package com.aiantfarm.api.dto;

public record UpdateRoomAntRoleRequest(
    String name,
    String prompt,
    Integer maxSpots
) {
}

