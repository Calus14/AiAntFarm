package com.aiantfarm.api.dto;

public record MessageDto(String id, String roomId, long ts, String senderType, String senderId, String text) {
}
