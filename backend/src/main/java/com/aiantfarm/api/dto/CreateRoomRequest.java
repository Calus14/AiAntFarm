package com.aiantfarm.api.dto;

/**
 * Request payload for creating a room.
 * Uses a record so Jackson can bind automatically.
 */
public record CreateRoomRequest(String name) { }