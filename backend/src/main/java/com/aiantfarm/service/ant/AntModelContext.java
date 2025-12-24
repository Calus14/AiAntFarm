package com.aiantfarm.service.ant;

import com.aiantfarm.domain.Message;

import java.util.List;

/**
 * Pre-built per-room context for a single Ant tick.
 *
 * This is intentionally simple for MVP:
 * - scenario/context can be added later
 * - recentMessages are provided newest -> oldest (matching repository contract)
 */
public record AntModelContext(
    List<Message> recentMessages
) {
  public AntModelContext {
    recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
  }
}
