package com.aiantfarm.repository.dynamo;

import com.aiantfarm.domain.RoomMembership;
import com.aiantfarm.domain.RoomRole;
import com.aiantfarm.repository.Page;
import com.aiantfarm.repository.RoomMembershipRepository;
import com.aiantfarm.repository.entity.RoomMembershipEntity;
import com.aiantfarm.utils.DynamoKeys;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RoomMembershipRepositoryImpl implements RoomMembershipRepository {

    private final DynamoDbTable<RoomMembershipEntity> table;

    public RoomMembershipRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(RoomMembershipEntity.class));
    }

    private static RoomMembershipEntity toEntity(RoomMembership m) {
        RoomMembershipEntity e = new RoomMembershipEntity();
        e.setPk(DynamoKeys.roomPk(m.roomId()));
        e.setSk(DynamoKeys.roomMemberSk(m.userId()));
        e.setRoomId(m.roomId());
        e.setUserId(m.userId());
        e.setRole(m.role().name());
        e.setJoinedAt(m.joinedAt());
        return e;
    }

    private static RoomMembership fromEntity(RoomMembershipEntity e) {
        String membershipId = e.getPk() + "|" + e.getSk();
        return new RoomMembership(
                membershipId,
                e.getRoomId(),
                e.getUserId(),
                e.getRole() != null ? RoomRole.valueOf(e.getRole()) : RoomRole.MEMBER,
                e.getJoinedAt() != null ? e.getJoinedAt() : Instant.EPOCH
        );
    }

    @Override
    public RoomMembership create(RoomMembership membership) {
        table.putItem(toEntity(membership));
        return membership;
    }

    @Override
    public Optional<RoomMembership> findById(String membershipId) {
        if (membershipId == null || !membershipId.contains("|")) return Optional.empty();
        String[] parts = membershipId.split("\\|", 2);
        String pk = parts[0];
        String sk = parts[1];

        RoomMembershipEntity e = table.getItem(r -> r.key(Key.builder().partitionValue(pk).sortValue(sk).build()));
        return Optional.ofNullable(e).map(RoomMembershipRepositoryImpl::fromEntity);
    }

    @Override
    public Page<RoomMembership> listByRoom(String roomId, int limit, String nextToken) {
        if (roomId == null || roomId.isBlank()) return new Page<>(List.of(), null);

        int pageSize = limit <= 0 ? 50 : limit;
        final String pk = DynamoKeys.roomPk(roomId);

        var res = table.query(r -> {
            r.queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(pk).build()));
            r.limit(pageSize);
            if (nextToken != null && !nextToken.isBlank()) {
                r.exclusiveStartKey(Map.of(
                    "pk", AttributeValue.builder().s(pk).build(),
                    "sk", AttributeValue.builder().s(nextToken).build()
                ));
            }
        });

        List<RoomMembership> items = new ArrayList<>();
        String outNext = null;
        for (var page : res) {
            for (var e : page.items()) {
                items.add(fromEntity(e));
            }
            if (page.lastEvaluatedKey() != null && page.lastEvaluatedKey().get("sk") != null) {
                outNext = page.lastEvaluatedKey().get("sk").s();
            }
            break;
        }

        return new Page<>(items, outNext);
    }

    @Override
    public Page<RoomMembership> listByUser(String userId, int limit, String nextToken) {
        if (userId == null || userId.isBlank()) return new Page<>(List.of(), null);

        PageIterable<RoomMembershipEntity> pages = table.scan();
        List<RoomMembership> items = new ArrayList<>();
        for (var page : pages) {
            for (var e : page.items()) {
                if (userId.equals(e.getUserId())) {
                    items.add(fromEntity(e));
                }
            }
        }

        return Page.of(items, null);
    }

    @Override
    public boolean delete(String membershipId) {
        if (membershipId == null || !membershipId.contains("|")) return false;
        String[] parts = membershipId.split("\\|", 2);
        String pk = parts[0];
        String sk = parts[1];
        table.deleteItem(r -> r.key(Key.builder().partitionValue(pk).sortValue(sk).build()));
        return true;
    }
}
