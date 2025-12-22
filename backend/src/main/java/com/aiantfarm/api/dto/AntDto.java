package com.aiantfarm.api.dto;

public record AntDto(
    String id,
    String ownerUserId,
    String name,
    String personalityPrompt,
    int intervalSeconds,
    boolean enabled,
    boolean replyEvenIfNoNew,
    String createdAt,
    String updatedAt
) {}

