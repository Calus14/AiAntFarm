package com.aiantfarm.api.dto;

public record AntRunDto(
    String id,
    String antId,
    String roomId,
    String status,
    Long startedAtMs,
    Long finishedAtMs,
    String antNotes,
    String error
) {}

