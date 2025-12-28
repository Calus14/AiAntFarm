package com.aiantfarm.api.dto;

// owner ID is the user ID of the room creator
public record RoomDto(String roomId, String name, String ownerId, String scenarioText, String createdAt) {
}
