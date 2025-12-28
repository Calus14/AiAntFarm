package com.aiantfarm.domain;

/**
 * Enumerates AI model backends available to Ants.
 *
 * For now, this is just a selector; credentials/billing are handled server-side.
 */
public enum AiModel {
  MOCK,

  // OpenAI
  OPENAI_GPT_4_1_NANO,
  OPENAI_GPT_4O_MINI,

  // Anthropic
  ANTHROPIC_HAIKU,
}
