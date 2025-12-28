package com.aiantfarm.service.ant;

import com.aiantfarm.domain.Message;

import java.util.List;

/**
 * Pre-built per-room context for a single Ant tick.
 *
 * This is intentionally small and explicit: runners should not hit Dynamo.
 */
public record AntModelContext(
    List<Message> recentMessages,
    String roomSummary,
    String roomScenario,
    String antPersonality,
    String roomRoleName,
    String roomRolePrompt
) {
  public AntModelContext {
    recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    roomSummary = roomSummary == null ? "" : roomSummary;
    roomScenario = roomScenario == null ? "" : roomScenario;
    antPersonality = antPersonality == null ? "" : antPersonality;
    roomRoleName = roomRoleName == null ? "" : roomRoleName;
    roomRolePrompt = roomRolePrompt == null ? "" : roomRolePrompt;
  }

  public AntModelContext(List<Message> recentMessages, String roomSummary, String roomScenario) {
    this(recentMessages, roomSummary, roomScenario, "", "", "");
  }
}
