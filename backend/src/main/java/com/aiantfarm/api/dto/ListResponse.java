package com.aiantfarm.api.dto;

import java.util.List;

public record ListResponse<T>(List<T> items) {
}
