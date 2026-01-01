package com.aiantfarm.api.dto;

import com.aiantfarm.domain.AiModel;

public record AntDto(
    String id,
    String ownerUserId,
    String name,
    AiModel model,
    String personalityPrompt,
    int intervalSeconds,
    boolean enabled,
    boolean replyEvenIfNoNew,
    int maxMessagesPerWeek,
    String createdAt,
    String updatedAt
) {}
