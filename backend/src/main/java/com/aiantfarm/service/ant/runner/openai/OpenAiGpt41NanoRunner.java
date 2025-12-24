package com.aiantfarm.service.ant.runner.openai;

import com.aiantfarm.domain.AiModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OpenAI: gpt-4.1-nano (cheap default)
 */
@Component
public class OpenAiGpt41NanoRunner extends AbstractOpenAiRunner {

  public OpenAiGpt41NanoRunner(
      @Value("${antfarm.models.openai.apiKey:${OPENAI_API_KEY:}}") String apiKey,
      @Value("${antfarm.models.openai.temperature:0.7}") double temperature,
      @Value("${antfarm.models.openai.maxTokens:256}") int maxTokens,
      @Value("${antfarm.models.openai.model.gpt41Nano:gpt-4.1-nano}") String modelId
  ) {
    super(apiKey, temperature, maxTokens, modelId);
  }

  @Override
  public AiModel model() {
    return AiModel.OPENAI_GPT_4_1_NANO;
  }
}

