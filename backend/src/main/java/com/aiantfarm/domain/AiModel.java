package com.aiantfarm.domain;

/**
 * Enumerates AI model backends available to Ants.
 *
 * For now, this is just a selector; credentials/billing are handled server-side.
 */
public enum AiModel {
  MOCK,

  // OpenAI examples (we'll wire actual integrations later)
  GPT_4O_MINI,

  // xAI examples ("Grok")
  GROK_2_MINI
}

