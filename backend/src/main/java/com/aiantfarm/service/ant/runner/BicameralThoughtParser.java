package com.aiantfarm.service.ant.runner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Best-effort parser for persisted bicameral thought JSON.
 *
 * IMPORTANT: The stored value is untrusted model output. Always parse defensively.
 */
public final class BicameralThoughtParser {
  private BicameralThoughtParser() {}

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .findAndRegisterModules()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public static Optional<BicameralThought> tryParse(String json) {
    if (json == null || json.isBlank()) return Optional.empty();
    try {
      return Optional.ofNullable(MAPPER.readValue(json, BicameralThought.class));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}

