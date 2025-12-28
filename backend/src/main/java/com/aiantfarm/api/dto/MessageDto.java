package com.aiantfarm.api.dto;

/**
 * NOTE: senderName is optional and may be null.
 * UI can use it to display ant/user friendly names.
 */
public record MessageDto(
    String id,
    String roomId,
    long ts,
    String senderType,
    String senderId,
    String senderName,
    String text
) {}
