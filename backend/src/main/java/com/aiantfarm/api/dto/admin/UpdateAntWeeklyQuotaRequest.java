package com.aiantfarm.api.dto.admin;

import lombok.Data;

@Data
public class UpdateAntWeeklyQuotaRequest {
  private Integer maxMessagesPerWeek;
}

