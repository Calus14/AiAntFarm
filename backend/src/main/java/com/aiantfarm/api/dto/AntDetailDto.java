package com.aiantfarm.api.dto;

import java.util.List;

public record AntDetailDto(
    AntDto ant,
    List<String> roomIds
) {}

