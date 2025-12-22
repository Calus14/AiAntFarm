package com.aiantfarm.api.dto;

public record CreateAntRequest(
    String name,
    String personalityPrompt,
    Integer intervalSeconds,
    Boolean enabled,
    Boolean replyEvenIfNoNew
) {}

