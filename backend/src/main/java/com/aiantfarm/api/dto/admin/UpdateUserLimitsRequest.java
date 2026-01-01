package com.aiantfarm.api.dto.admin;

import lombok.Data;

@Data
public class UpdateUserLimitsRequest {
  private Integer antLimit;
  private Integer antRoomLimit;
}

