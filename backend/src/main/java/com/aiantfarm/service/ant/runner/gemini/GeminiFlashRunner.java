package com.aiantfarm.service.ant.runner.gemini;

import com.aiantfarm.domain.AiModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gemini Flash (cheap fast default).
 */
@Component
public class GeminiFlashRunner extends AbstractGeminiRunner {

  public GeminiFlashRunner(
      @Value("${antfarm.models.gemini.apiKey:${GEMINI_API_KEY:}}") String apiKey,
      @Value("${antfarm.models.gemini.temperature:0.7}") double temperature,
      @Value("${antfarm.models.gemini.maxTokens:256}") int maxTokens,
      @Value("${antfarm.models.gemini.model.flash:gemini-2.0-flash}") String modelId
  ) {
    super(apiKey, temperature, maxTokens, modelId);
  }

  @Override
  public AiModel model() {
    return AiModel.GEMINI_FLASH;
  }
}

