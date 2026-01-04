package com.aiantfarm.service.ant.runner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Internal-only per-ant reflective state used to steer future message generation.
 *
 * IMPORTANT: This is not exposed via API/DTOs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BicameralThought(
    int version,
    Instant createdAt,
    int stalenessScore,          // 0-100
    int confidenceScore,         // 0-100
    String lastMessageIntent,    // <= 75 chars
    String myReplyIntent,        // <= 75 chars
    int voiceAuthenticityScore,  // 0-100
    List<String> voiceNotes,     // max 2
    List<String> adjacentTopicCandidates, // max 2
    String nextTopicAnchor       // <= 80 chars
) {
  public static final int CURRENT_VERSION = 2;

  public BicameralThought {
    version = version <= 0 ? CURRENT_VERSION : version;
    createdAt = createdAt == null ? Instant.now() : createdAt;

    stalenessScore = clamp0to100(stalenessScore);
    confidenceScore = clamp0to100(confidenceScore);
    voiceAuthenticityScore = clamp0to100(voiceAuthenticityScore);

    lastMessageIntent = trimToMax(lastMessageIntent, 75);
    myReplyIntent = trimToMax(myReplyIntent, 75);
    nextTopicAnchor = trimToMax(nextTopicAnchor, 80);

    voiceNotes = capList(voiceNotes, 2, 80);
    adjacentTopicCandidates = capList(adjacentTopicCandidates, 2, 80);
  }

  private static int clamp0to100(int v) {
    if (v < 0) return 0;
    if (v > 100) return 100;
    return v;
  }

  private static String trimToMax(String s, int max) {
    if (s == null) return "";
    String t = s.trim();
    if (t.length() <= max) return t;
    return t.substring(0, max).trim();
  }

  private static List<String> capList(List<String> in, int maxItems, int maxCharsEach) {
    if (in == null || in.isEmpty()) return List.of();
    return in.stream()
        .filter(x -> x != null && !x.isBlank())
        .map(x -> trimToMax(x, maxCharsEach))
        .limit(maxItems)
        .toList();
  }
}
