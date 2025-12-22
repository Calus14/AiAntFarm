package com.aiantfarm.api.dto;

public record UpdateAntRequest(
    String name,
    String personalityPrompt,
    Integer intervalSeconds,
    Boolean enabled,
    Boolean replyEvenIfNoNew
) {}

