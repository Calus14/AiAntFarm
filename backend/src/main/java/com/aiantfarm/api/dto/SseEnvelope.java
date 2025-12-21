package com.aiantfarm.api.dto;

/**
 * Simple SSE payload wrapper so clients can reliably parse events.
 *
 * @param type    logical type of the event (e.g. "message")
 * @param payload actual payload object (e.g. MessageDto)
 */
public record SseEnvelope<T>(String type, T payload) {
}

