package com.aiantfarm.store;

import com.aiantfarm.domain.RoomMembership;
import java.util.Optional;

public interface MembershipStore {
    RoomMembership create(RoomMembership membership);
    Optional<RoomMembership> findById(String tenantId, String membershipId);
    Page<RoomMembership> listByRoom(String tenantId, String roomId, int limit, String nextToken);
    Page<RoomMembership> listByUser(String tenantId, String userId, int limit, String nextToken);
    boolean delete(String tenantId, String membershipId);
}
