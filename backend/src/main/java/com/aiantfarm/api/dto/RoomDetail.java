package com.aiantfarm.api.dto;

import java.util.List;

public record RoomDetail(Room room, List<Message> messages) {}

