package com.aiantfarm.api.dto;

import com.aiantfarm.domain.AiModel;
import lombok.Data;

@Data
public class UpdateAntRequest {
  private String name;
  private AiModel model;
  private String personalityPrompt;
  private Integer intervalSeconds;
  private Boolean enabled;
  private Boolean replyEvenIfNoNew;
  private Integer maxMessagesPerWeek;
}
