package com.aiantfarm.repository.dynamo;

import com.aiantfarm.domain.RoomAntRole;
import com.aiantfarm.repository.RoomAntRoleRepository;
import com.aiantfarm.repository.entity.RoomAntRoleEntity;
import com.aiantfarm.utils.DynamoKeys;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoomAntRoleRepositoryImpl implements RoomAntRoleRepository {

  private final DynamoDbTable<RoomAntRoleEntity> table;

  public RoomAntRoleRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(RoomAntRoleEntity.class));
  }

  @Override
  public RoomAntRole create(RoomAntRole role) {
    table.putItem(toEntity(role));
    return role;
  }

  @Override
  public RoomAntRole update(RoomAntRole role) {
    table.updateItem(toEntity(role));
    return role;
  }

  @Override
  public Optional<RoomAntRole> find(String roomId, String roleId) {
    if (roomId == null || roomId.isBlank() || roleId == null || roleId.isBlank()) return Optional.empty();

    RoomAntRoleEntity e = table.getItem(r -> r.key(Key.builder()
        .partitionValue(DynamoKeys.roomPk(roomId))
        .sortValue(DynamoKeys.roomAntRoleSk(roleId))
        .build()));

    return Optional.ofNullable(e).map(RoomAntRoleRepositoryImpl::fromEntity);
  }

  @Override
  public List<RoomAntRole> listByRoom(String roomId) {
    if (roomId == null || roomId.isBlank()) return List.of();

    var res = table.query(r -> r.queryConditional(
        QueryConditional.sortBeginsWith(Key.builder()
            .partitionValue(DynamoKeys.roomPk(roomId))
            .sortValue("ANTROLE#")
            .build())));

    List<RoomAntRole> out = new ArrayList<>();
    for (var page : res) {
      for (var e : page.items()) {
        out.add(fromEntity(e));
      }
    }
    return out;
  }

  @Override
  public boolean delete(String roomId, String roleId) {
    if (roomId == null || roomId.isBlank() || roleId == null || roleId.isBlank()) return false;

    table.deleteItem(r -> r.key(Key.builder()
        .partitionValue(DynamoKeys.roomPk(roomId))
        .sortValue(DynamoKeys.roomAntRoleSk(roleId))
        .build()));

    return true;
  }

  private static RoomAntRoleEntity toEntity(RoomAntRole r) {
    RoomAntRoleEntity e = new RoomAntRoleEntity();
    e.setPk(DynamoKeys.roomPk(r.roomId()));
    e.setSk(DynamoKeys.roomAntRoleSk(r.roleId()));
    e.setRoomId(r.roomId());
    e.setRoleId(r.roleId());
    e.setName(r.name());
    e.setPrompt(r.prompt());
    e.setMaxSpots(r.maxSpots());
    e.setCreatedAt(r.createdAt());
    e.setUpdatedAt(r.updatedAt());
    return e;
  }

  private static RoomAntRole fromEntity(RoomAntRoleEntity e) {
    return new RoomAntRole(
        e.getRoomId(),
        e.getRoleId(),
        e.getName(),
        e.getPrompt(),
        e.getMaxSpots(),
        e.getCreatedAt(),
        e.getUpdatedAt()
    );
  }
}

