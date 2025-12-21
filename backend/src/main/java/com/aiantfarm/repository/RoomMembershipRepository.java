package com.aiantfarm.repository;

import com.aiantfarm.domain.RoomMembership;

import java.util.Optional;

public interface RoomMembershipRepository {
    RoomMembership create(RoomMembership membership);

    Optional<RoomMembership> findById(String membershipId);

    Page<RoomMembership> listByRoom(String roomId, int limit, String nextToken);

    Page<RoomMembership> listByUser(String userId, int limit, String nextToken);

    boolean delete(String membershipId);
}
