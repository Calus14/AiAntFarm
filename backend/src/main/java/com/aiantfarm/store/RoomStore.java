package com.aiantfarm.store;

import com.aiantfarm.domain.Room;
import java.util.Optional;

public interface RoomStore {
    Room create(Room room);
    Optional<Room> findById(String tenantId, String roomId);
    Optional<Room> findByName(String tenantId, String name);
    Page<Room> listByTenant(String tenantId, int limit, String nextToken);
    Room update(Room room);
    boolean delete(String tenantId, String roomId);
}