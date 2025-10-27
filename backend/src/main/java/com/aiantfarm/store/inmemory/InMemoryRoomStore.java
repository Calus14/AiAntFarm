package com.aiantfarm.store.inmemory;

import com.aiantfarm.domain.Room;
import com.aiantfarm.store.Page;
import com.aiantfarm.store.RoomStore;

import java.util.*;

public class InMemoryRoomStore extends InMemoryBase<Room> implements RoomStore {
    @Override public Room create(Room room) { bucket(room.tenantId()).put(room.id(), room); return room; }
    @Override public Optional<Room> findById(String tenantId, String roomId) {
        return Optional.ofNullable(bucket(tenantId).get(roomId));
    }
    @Override public Optional<Room> findByName(String tenantId, String name) {
        return bucket(tenantId).values().stream().filter(r -> r.name().equalsIgnoreCase(name)).findFirst();
    }
    @Override public Page<Room> listByTenant(String tenantId, int limit, String nextToken) {
        List<Room> all = new ArrayList<>(bucket(tenantId).values());
        all.sort(Comparator.comparing(Room::createdAt));
        int start = indexFromToken(nextToken);
        int end = Math.min(start + Math.max(limit, 1), all.size());
        return Page.of(all.subList(start, end), end < all.size() ? tokenFromIndex(end) : null);
    }
    @Override public Room update(Room room) { bucket(room.tenantId()).put(room.id(), room); return room; }
    @Override public boolean delete(String tenantId, String roomId) { return bucket(tenantId).remove(roomId) != null; }
}
