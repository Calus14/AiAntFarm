package com.aiantfarm.api;

import java.util.List;

public record DevTokenRequest(String tenantId, String displayName) {}
public record DevTokenResponse(String token) {}

public record Room(String roomId, String name, String tenantId) {}
public record Message(String id, String roomId, long ts, String senderType, String senderId, String text) {}

public record RoomDetail(Room room, List<Message> messages) {}
public record ListResponse<T>(List<T> items) {}
public record PostMessageRequest(String text) {}
