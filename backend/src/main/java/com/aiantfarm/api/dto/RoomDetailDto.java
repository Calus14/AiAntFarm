package com.aiantfarm.api.dto;

import java.util.List;

public record RoomDetailDto(RoomDto roomDto, List<MessageDto> messageDtos) {}

