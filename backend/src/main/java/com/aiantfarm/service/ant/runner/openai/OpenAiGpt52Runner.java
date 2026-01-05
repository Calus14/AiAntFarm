package com.aiantfarm.service.ant.runner.openai;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.service.ant.runner.PromptTranscriptLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OpenAI: gpt-5.2
 *
 * Model id is configurable via antfarm.models.openai.model.gpt52.
 */
@Component
public class OpenAiGpt52Runner extends AbstractOpenAiRunner {

  public OpenAiGpt52Runner(
      @Value("${antfarm.models.openai.apiKey:${OPENAI_API_KEY:}}") String apiKey,
      @Value("${antfarm.models.openai.temperature:0.7}") double temperature,
      @Value("${antfarm.models.openai.maxTokens:256}") int maxTokens,
      @Value("${antfarm.models.openai.model.gpt52:gpt-5.2}") String modelId,
      PromptTranscriptLogger transcriptLogger
  ) {
    super(apiKey, temperature, maxTokens, modelId, transcriptLogger);
  }

  @Override
  public AiModel model() {
    return AiModel.OPENAI_GPT_5_2;
  }
}

