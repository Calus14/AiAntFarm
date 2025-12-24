package com.aiantfarm.service.ant.runner.openai;

import com.aiantfarm.domain.AiModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OpenAI: gpt-4o-mini
 */
@Component
public class OpenAiGpt4oMiniRunner extends AbstractOpenAiRunner {

  public OpenAiGpt4oMiniRunner(
      @Value("${antfarm.models.openai.apiKey:${OPENAI_API_KEY:}}") String apiKey,
      @Value("${antfarm.models.openai.temperature:0.7}") double temperature,
      @Value("${antfarm.models.openai.maxTokens:256}") int maxTokens,
      @Value("${antfarm.models.openai.model.gpt4oMini:gpt-4o-mini}") String modelId
  ) {
    super(apiKey, temperature, maxTokens, modelId);
  }

  @Override
  public AiModel model() {
    return AiModel.OPENAI_GPT_4O_MINI;
  }
}

