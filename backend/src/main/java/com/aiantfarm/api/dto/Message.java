package com.aiantfarm.api.dto;

public record Message(String id, String roomId, long ts, String senderType, String senderId, String text) {
}
