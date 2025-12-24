package com.aiantfarm.service.ant.runner.anthropic;

import com.aiantfarm.domain.AiModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Anthropic Haiku-class model.
 */
@Component
public class AnthropicHaikuRunner extends AbstractAnthropicRunner {

  public AnthropicHaikuRunner(
      @Value("${antfarm.models.anthropic.apiKey:${ANTHROPIC_API_KEY:}}") String apiKey,
      @Value("${antfarm.models.anthropic.temperature:0.7}") double temperature,
      @Value("${antfarm.models.anthropic.maxTokens:256}") int maxTokens,
      @Value("${antfarm.models.anthropic.model.haiku:claude-3-5-haiku-latest}") String modelId
  ) {
    super(apiKey, temperature, maxTokens, modelId);
  }

  @Override
  public AiModel model() {
    return AiModel.ANTHROPIC_HAIKU;
  }
}

