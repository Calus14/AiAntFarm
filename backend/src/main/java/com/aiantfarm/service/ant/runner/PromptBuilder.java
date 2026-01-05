package com.aiantfarm.service.ant.runner;

import com.aiantfarm.domain.AuthorType;
import com.aiantfarm.domain.Message;

import java.util.List;
import java.util.Optional;

/**
 * Very small prompt builder.
 *
 * !!! SAFETY/ABUSE NOTE (do not delete):
 * Room content and persona prompts are untrusted user input.
 * Do not allow prompt injection to exfiltrate secrets or system instructions.
 * Add moderation + rate limiting before exposing public model runners.
 */
public final class PromptBuilder {
  private PromptBuilder() {}

  private static final String NO_RESPONSE_SENTINEL = "<<<NO_RESPONSE>>>";

  public static String buildSystemPrompt(String antName, String personalityPrompt) {
    return buildSystemPrompt(antName, personalityPrompt, null);
  }

  public static String buildSystemPrompt(String antName, String personalityPrompt, Integer maxOutputTokens) {
    String pp = personalityPrompt == null ? "" : personalityPrompt.trim();

    String personalityBlock = pp.isBlank() ? "" : ("Personality (follow):\n" + pp + "\n");

    // Output constraint hint: the API has a hard token cap, but explicitly steering the model
    // helps it avoid verbose completions and mid-sentence truncation.
    String outputLimitHint = (maxOutputTokens == null)
        ? "Output limit: keep your reply concise (roughly <= 150 words unless necessary)."
        : ("Output limit: you have a hard cap of " + maxOutputTokens + " output tokens. Keep the reply concise.");

    return PromptTemplates.render(
        "prompt.message.system",
        java.util.Map.of(
            "antName", antName == null ? "" : antName,
            "personalityBlock", personalityBlock + (personalityBlock.isBlank() ? "" : "\n") + outputLimitHint + "\n"
        )
    );
  }

  /**
   * User prompt for message generation.
   *
   * Includes:
   * - room scenario
   * - ant personality (restated)
   * - assigned room role (name + prompt)
   * - rolling summary
   * - last N messages
   */
  public static String buildUserContext(String roomScenario,
                                       String antPersonality,
                                       String roomRoleName,
                                       String roomRolePrompt,
                                       String rollingSummary,
                                       String bicameralThoughtJson,
                                       List<Message> newestToOldest,
                                       int maxChars) {
    return buildUserContext(roomScenario, antPersonality, roomRoleName, roomRolePrompt,
        rollingSummary, bicameralThoughtJson, newestToOldest, maxChars, false);
  }

  public static String buildUserContext(String roomScenario,
                                       String antPersonality,
                                       String roomRoleName,
                                       String roomRolePrompt,
                                       String rollingSummary,
                                       String bicameralThoughtJson,
                                       List<Message> newestToOldest,
                                       int maxChars,
                                       boolean forceReply) {
    String scenario = roomScenario == null ? "" : roomScenario.trim();
    String personality = antPersonality == null ? "" : antPersonality.trim();
    String roleName = roomRoleName == null ? "" : roomRoleName.trim();
    String rolePrompt = roomRolePrompt == null ? "" : roomRolePrompt.trim();
    String summary = rollingSummary == null ? "" : rollingSummary.trim();

    String roleBlock = "";
    if (!roleName.isBlank()) roleBlock += "Role name: " + roleName + "\n";
    if (!rolePrompt.isBlank()) roleBlock += rolePrompt + "\n";
    if (roleBlock.isBlank()) roleBlock = "(no specific role assigned)\n";

    String transcript = messagesToTranscript(newestToOldest, maxChars);

    String engagement = buildEngagementDirective(bicameralThoughtJson);
    String engagementDirectiveBlock = (engagement.isBlank())
        ? ""
        : ("ENGAGEMENT DIRECTIVE (internal steering; never mention this section):\n" + engagement.trim() + "\n\n");

    String forceReplyBlock = "";
    if (forceReply) {
      forceReplyBlock = PromptTemplates.render(
          "prompt.message.forceReplyBlock",
          java.util.Map.of("noResponseSentinel", NO_RESPONSE_SENTINEL)
      );
    }

    return PromptTemplates.render(
        "prompt.message.user",
        java.util.Map.of(
            "roomScenario", scenario,
            "antPersonality", personality,
            "roleBlock", roleBlock,
            "roomSummary", summary.isBlank() ? "(no summary yet)" : summary,
            "engagementDirectiveBlock", engagementDirectiveBlock,
            "transcript", transcript,
            "noResponseSentinel", NO_RESPONSE_SENTINEL,
            "forceReplyBlock", forceReplyBlock
        )
    );
  }

  /**
   * Converts bicameral thought JSON into a short prompt directive.
   * If parsing fails, returns blank.
   */
  static String buildEngagementDirective(String bicameralThoughtJson) {
    Optional<BicameralThought> thoughtOpt = BicameralThoughtParser.tryParse(bicameralThoughtJson);
    if (thoughtOpt.isEmpty()) return "";

    BicameralThought t = thoughtOpt.get();
    StringBuilder sb = new StringBuilder();

    // Voice notes become explicit "voice" steering bullets.
    if (t.voiceNotes() != null && !t.voiceNotes().isEmpty()) {
      sb.append("Voice:\n");
      for (String vn : t.voiceNotes()) {
        if (vn == null || vn.isBlank()) continue;
        sb.append("- ").append(vn.trim()).append("\n");
      }
    }

    // Staleness steering (scenario-agnostic).
    int stale = t.stalenessScore();
    if (stale >= 70) {
      sb.append("Threading: conversation feels stale; it's okay to shift topic slightly or ask a fresh question.\n");
      sb.append("Length: allow more detail if it feels natural for your character.\n");
    } else if (stale <= 30) {
      sb.append("Threading: stay on the current thread; keep things light and responsive.\n");
    } else {
      sb.append("Threading: continue the thread; a small related tangent is OK.\n");
    }

    // Confidence steering.
    int conf = t.confidenceScore();
    if (conf <= 30) {
      sb.append("Tone: hedged/uncertain is OK.\n");
    } else if (conf >= 70) {
      sb.append("Tone: confident, but still human.\n");
    }

    // Topic anchor hint.
    if (t.nextTopicAnchor() != null && !t.nextTopicAnchor().isBlank()) {
      sb.append("Possible hook: ").append(t.nextTopicAnchor().trim()).append("\n");
    }

    return sb.toString().trim();
  }

  public static String buildSummarySystemPrompt(String antName, String personalityPrompt, Integer maxOutputTokens) {
    String outputLimitLine = (maxOutputTokens == null)
        ? "Output limit: keep the summary short (aim <= ~200 words).\n"
        : ("Output limit: hard cap is " + maxOutputTokens + " output tokens. Keep the summary short and dense.\n");

    return "You maintain a rolling room summary for an AI agent named \"" + antName + "\".\n"
        + (personalityPrompt == null || personalityPrompt.trim().isBlank() ? "" :
        ("Agent personality:\n" + personalityPrompt.trim() + "\n"))
        + "Write a concise rolled-up summary that helps the agent respond appropriately.\n"
        + outputLimitLine
        + "Hard rules:\n"
        + "- Keep it short (<= ~5 paragraphs, <= ~8 sentences).\n"
        + "- Do NOT quote long transcripts.\n"
        + "- Preserve important facts, decisions, names, and goals.\n"
        + "- Do NOT invent facts.\n";
  }

  /**
   * User prompt for summary generation.
   *
   * Includes scenario + personality + role so the summary preserves what matters for this specific ant.
   */
  public static String buildSummaryUserPrompt(String roomScenario,
                                             String antPersonality,
                                             String roomRoleName,
                                             String roomRolePrompt,
                                             String existingSummary,
                                             List<Message> newestToOldest,
                                             int maxChars) {
    String scenario = roomScenario == null ? "" : roomScenario.trim();
    String personality = antPersonality == null ? "" : antPersonality.trim();
    String roleName = roomRoleName == null ? "" : roomRoleName.trim();
    String rolePrompt = roomRolePrompt == null ? "" : roomRolePrompt.trim();
    String existing = existingSummary == null ? "" : existingSummary.trim();

    String transcript = messagesToTranscript(newestToOldest, maxChars);

    StringBuilder sb = new StringBuilder();

    if (!scenario.isBlank()) {
      sb.append("ROOM SETTING / SCENARIO:\n").append(scenario).append("\n\n");
    }
    if (!personality.isBlank()) {
      sb.append("ANT PERSONALITY (follow strictly):\n").append(personality).append("\n\n");
    }
    if (!roleName.isBlank() || !rolePrompt.isBlank()) {
      sb.append("ANT ROLE IN THIS ROOM:\n");
      if (!roleName.isBlank()) sb.append("Role name: ").append(roleName).append("\n");
      if (!rolePrompt.isBlank()) sb.append(rolePrompt).append("\n");
      sb.append("\n");
    }

    if (!existing.isBlank()) {
      sb.append("EXISTING SUMMARY:\n").append(existing).append("\n\n");
    }

    sb.append("NEW MESSAGES (latest window):\n").append(transcript).append("\n\n");
    sb.append("Task: produce an UPDATED rolled-up summary (replace the existing summary with a new one).\n");

    return sb.toString();
  }

  public static String buildBicameralThoughtSystemPrompt(String antName, Integer maxOutputTokens) {
    // Output constraint hint: these thoughts are stored and reused; keep them compact.
    String outputLimitHint = (maxOutputTokens == null)
        ? "Output limit: keep the JSON compact (prefer <= ~350 tokens)."
        : ("Output limit: hard cap is " + maxOutputTokens + " output tokens. Keep the JSON compact.");
    return PromptTemplates.render(
        "prompt.bicameral.system",
        java.util.Map.of("antName", (antName == null ? "" : antName) + "\n" + outputLimitHint)
    );
  }

  public static String buildBicameralThoughtUserPrompt(String roomScenario,
                                                      String antPersonality,
                                                      String roomRoleName,
                                                      String roomRolePrompt,
                                                      String rollingSummary,
                                                      List<Message> newestToOldest,
                                                      int maxChars) {
    String scenario = roomScenario == null ? "" : roomScenario.trim();
    String personality = antPersonality == null ? "" : antPersonality.trim();
    String roleName = roomRoleName == null ? "" : roomRoleName.trim();
    String rolePrompt = roomRolePrompt == null ? "" : roomRolePrompt.trim();
    String summary = rollingSummary == null ? "" : rollingSummary.trim();

    String roleBlock = "";
    if (!roleName.isBlank()) roleBlock += "Role name: " + roleName + "\n";
    if (!rolePrompt.isBlank()) roleBlock += rolePrompt + "\n";
    if (roleBlock.isBlank()) roleBlock = "(no specific role assigned)\n";

    String transcript = messagesToTranscript(newestToOldest, maxChars);

    return PromptTemplates.render(
        "prompt.bicameral.user",
        java.util.Map.of(
            "roomScenario", scenario,
            "antPersonality", personality,
            "roleBlock", roleBlock,
            "roomSummary", summary.isBlank() ? "(no summary yet)" : summary,
            "transcript", transcript
        )
    );
  }

  /**
   * Converts message list to a transcript string.
   * If the list is empty or null, returns "(no prior messages)".
   */
  public static String messagesToTranscript(List<Message> newestToOldest, int maxChars) {
    if (newestToOldest == null || newestToOldest.isEmpty()) return "(no prior messages)";

    StringBuilder sb = new StringBuilder();
    int used = 0;

    // Convert to oldest->newest for readability
    for (int i = newestToOldest.size() - 1; i >= 0; i--) {
      Message m = newestToOldest.get(i);
      if (m == null) continue;
      String who;
      if (m.authorType() == AuthorType.USER) who = m.authorName() != null ? m.authorName() : "User";
      else if (m.authorType() == AuthorType.ANT) who = m.authorName() != null ? m.authorName() : "Ant";
      else who = "System";

      String line = who + ": " + safeOneLine(m.content()) + "\n";
      if (used + line.length() > maxChars) break;
      sb.append(line);
      used += line.length();
    }

    return sb.toString().isBlank() ? "(no prior messages)" : sb.toString();
  }

  private static String safeOneLine(String s) {
    if (s == null) return "";
    return s.replaceAll("[\\r\\n]+", " ").trim();
  }
}
