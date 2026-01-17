package com.aiantfarm.repository.dynamo;

import com.aiantfarm.domain.User;
import com.aiantfarm.repository.UserRepository;
import com.aiantfarm.repository.entity.UserEntity;
import com.aiantfarm.utils.DynamoKeys;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.Optional;

public class UserRepositoryImpl implements UserRepository {

    private final DynamoDbTable<UserEntity> table;

    public UserRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(UserEntity.class));
    }

    private static UserEntity toEntity(User u) {
        UserEntity e = new UserEntity();
        e.setPk(DynamoKeys.userPk(u.id()));
        e.setSk(DynamoKeys.userProfileSk(u.id()));
        e.setUserEmail(u.userEmail());
        e.setDisplayName(u.displayName());
        e.setActive(u.active());
        e.setCreatedAt(u.createdAt());
        e.setAntLimit(u.antLimit());
        e.setAntRoomLimit(u.antRoomLimit());
        e.setRoomLimit(u.roomLimit());
        return e;
    }

    private static User fromEntity(UserEntity e) {
        // The domain ID should be the raw UUID, not the prefixed PK.
        String userId = e.getPk() != null && e.getPk().startsWith("USER#")
            ? e.getPk().substring("USER#".length())
            : e.getPk();

        return new User(
                userId,
                e.getUserEmail(),
                e.getDisplayName(),
                e.getCreatedAt() != null ? e.getCreatedAt() : Instant.EPOCH,
                e.isActive(),
                e.getAntLimit(),
                e.getAntRoomLimit(),
                e.getRoomLimit()
        );
    }

    @Override
    public User create(User user) {
        table.putItem(toEntity(user));
        return user;
    }

    @Override
    public Optional<User> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();

        UserEntity e = table.getItem(r -> r.key(Key.builder()
            .partitionValue(DynamoKeys.userPk(userId))
            .sortValue(DynamoKeys.userProfileSk(userId))
            .build()));

        return Optional.ofNullable(e).map(UserRepositoryImpl::fromEntity);
    }

    @Override
    public User update(User user) {
        table.updateItem(toEntity(user));
        return user;
    }

    @Override
    public boolean deleteByUserId(String userId) {
        if (userId == null || userId.isBlank()) return false;
        table.deleteItem(r -> r.key(Key.builder()
            .partitionValue(DynamoKeys.userPk(userId))
            .sortValue(DynamoKeys.userProfileSk(userId))
            .build()));
        return true;
    }

    @Override
    public long countUsers() {
        long count = 0;
        for (var page : table.scan()) {
            for (var e : page.items()) {
                if (e == null) continue;
                String pk = e.getPk();
                String sk = e.getSk();
                if (pk != null && pk.startsWith("USER#") && sk != null && sk.startsWith("PROFILE#")) {
                    count++;
                }
            }
        }
        return count;
    }
}
