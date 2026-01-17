package com.aiantfarm.service.ant.runner.openai;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.service.ant.runner.PromptTranscriptLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OpenAI: gpt-5-mini (economical GPT-5 family option)
 *
 * Model id is configurable via antfarm.models.openai.model.gpt5oMini.
 */
@Component
public class OpenAiGpt5oMiniRunner extends AbstractOpenAiRunner {

  public OpenAiGpt5oMiniRunner(
      @Value("${antfarm.models.openai.apiKey:${OPENAI_API_KEY:}}") String apiKey,
      @Value("${antfarm.models.openai.temperature:0.7}") double temperature,
      @Value("${antfarm.models.openai.maxTokens:256}") int maxTokens,
      @Value("${antfarm.models.openai.outputLimits.messageMaxTokens:256}") int messageMaxTokens,
      @Value("${antfarm.models.openai.maxAttempts:3}") int maxAttempts,
      @Value("${antfarm.models.openai.model.gpt5oMini:gpt-5-mini}") String modelId,
      @Value("${antfarm.models.openai.outputLimits.summaryMaxTokens:450}") int summaryMaxTokens,
      @Value("${antfarm.models.openai.outputLimits.summaryMaxTokensCap:600}") int summaryMaxTokensCap,
      @Value("${antfarm.models.openai.outputLimits.thoughtMaxTokens:400}") int thoughtMaxTokens,
      @Value("${antfarm.models.openai.outputLimits.thoughtMaxTokensCap:600}") int thoughtMaxTokensCap,
      PromptTranscriptLogger transcriptLogger
  ) {
    super(apiKey, temperature, maxTokens, modelId,
        summaryMaxTokens, summaryMaxTokensCap,
        thoughtMaxTokens, thoughtMaxTokensCap,
        maxAttempts,
        transcriptLogger);
  }

  @Override
  public AiModel model() {
    return AiModel.OPENAI_GPT_5O_MINI;
  }
}
