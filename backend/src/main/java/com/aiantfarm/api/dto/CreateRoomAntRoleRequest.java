package com.aiantfarm.api.dto;

public record CreateRoomAntRoleRequest(
    String name,
    String prompt,
    Integer maxSpots
) {
}

