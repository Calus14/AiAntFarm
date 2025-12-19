package com.aiantfarm.repository;

import com.aiantfarm.domain.Room;

import java.util.Optional;

public interface RoomRepository {
    Room create(Room room);
    Optional<Room> findById(String roomId);
    Optional<Room> findByName(String name);
    Page<Room> listByUserCreatedId(String userId, int limit, String nextToken);
    Room update(Room room);
    boolean deleteByRoomId(String roomId);
}

