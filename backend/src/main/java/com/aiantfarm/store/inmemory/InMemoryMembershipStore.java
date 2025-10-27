package com.aiantfarm.store.inmemory;

import com.aiantfarm.domain.RoomMembership;
import com.aiantfarm.store.MembershipStore;
import com.aiantfarm.store.Page;

import java.util.*;

public class InMemoryMembershipStore extends InMemoryBase<RoomMembership> implements MembershipStore {
    @Override public RoomMembership create(RoomMembership m) { bucket(m.tenantId()).put(m.id(), m); return m; }
    @Override public Optional<RoomMembership> findById(String tenantId, String membershipId) {
        return Optional.ofNullable(bucket(tenantId).get(membershipId));
    }
    @Override public Page<RoomMembership> listByRoom(String tenantId, String roomId, int limit, String nextToken) {
        List<RoomMembership> all = new ArrayList<>(bucket(tenantId).values());
        all.removeIf(m -> !m.roomId().equals(roomId));
        all.sort(Comparator.comparing(RoomMembership::joinedAt));
        int start = indexFromToken(nextToken);
        int end = Math.min(start + Math.max(limit, 1), all.size());
        return Page.of(all.subList(start, end), end < all.size() ? tokenFromIndex(end) : null);
    }
    @Override public Page<RoomMembership> listByUser(String tenantId, String userId, int limit, String nextToken) {
        List<RoomMembership> all = new ArrayList<>(bucket(tenantId).values());
        all.removeIf(m -> !m.userId().equals(userId));
        all.sort(Comparator.comparing(RoomMembership::joinedAt));
        int start = indexFromToken(nextToken);
        int end = Math.min(start + Math.max(limit, 1), all.size());
        return Page.of(all.subList(start, end), end < all.size() ? tokenFromIndex(end) : null);
    }
    @Override public boolean delete(String tenantId, String membershipId) {
        return bucket(tenantId).remove(membershipId) != null;
    }
}
