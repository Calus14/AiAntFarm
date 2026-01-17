package com.aiantfarm.domain;

/**
 * Enumerates AI model backends available to Ants.
 *
 * For now, this is just a selector; credentials/billing are handled server-side.
 */
public enum AiModel {
  // OpenAI - Currently the default models for Ants
  OPENAI_GPT_4_1_NANO,
  OPENAI_GPT_4O_MINI,
  OPENAI_GPT_5O_MINI,
  OPENAI_GPT_5_2,

  // Anthropic
  ANTHROPIC_HAIKU,
}
