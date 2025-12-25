package com.aiantfarm.repository.dynamo;

import com.aiantfarm.domain.AntRoomAssignment;
import com.aiantfarm.repository.AntRoomAssignmentRepository;
import com.aiantfarm.repository.entity.AntRoomAssignmentEntity;
import com.aiantfarm.utils.DynamoKeys;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.aiantfarm.utils.DynamoIndexes.GSI_ROOM_ID;

public class AntRoomAssignmentRepositoryImpl implements AntRoomAssignmentRepository {

  private final DynamoDbTable<AntRoomAssignmentEntity> table;
  private final DynamoDbIndex<AntRoomAssignmentEntity> roomIndex;

  public AntRoomAssignmentRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(AntRoomAssignmentEntity.class));
    this.roomIndex = table.index(GSI_ROOM_ID);
  }

  @Override
  public AntRoomAssignment assign(AntRoomAssignment assignment) {
    table.putItem(toEntity(assignment));
    return assignment;
  }

  @Override
  public void unassign(String antId, String roomId) {
    if (antId == null || antId.isBlank() || roomId == null || roomId.isBlank()) return;

    table.deleteItem(r -> r.key(Key.builder()
        .partitionValue(DynamoKeys.antPk(antId))
        .sortValue(DynamoKeys.antRoomSk(roomId))
        .build()));
  }

  @Override
  public Optional<AntRoomAssignment> find(String antId, String roomId) {
    if (antId == null || antId.isBlank() || roomId == null || roomId.isBlank()) return Optional.empty();

    AntRoomAssignmentEntity e = table.getItem(r -> r.key(Key.builder()
        .partitionValue(DynamoKeys.antPk(antId))
        .sortValue(DynamoKeys.antRoomSk(roomId))
        .build()));

    return Optional.ofNullable(e).map(AntRoomAssignmentRepositoryImpl::fromEntity);
  }

  @Override
  public List<AntRoomAssignment> listByAnt(String antId) {
    if (antId == null || antId.isBlank()) return List.of();

    var res = table.query(r -> {
      r.queryConditional(QueryConditional.sortBeginsWith(
          Key.builder().partitionValue(DynamoKeys.antPk(antId)).sortValue("ROOM#").build()));
    });

    List<AntRoomAssignment> out = new ArrayList<>();
    for (var page : res) {
      for (var e : page.items()) {
        if (e.getSk() != null && e.getSk().startsWith("ROOM#")) {
          out.add(fromEntity(e));
        }
      }
    }
    return out;
  }

  @Override
  public List<AntRoomAssignment> listByRoom(String roomId) {
    if (roomId == null || roomId.isBlank()) return List.of();

    var res = roomIndex.query(r -> r.queryConditional(
        QueryConditional.keyEqualTo(Key.builder().partitionValue(DynamoKeys.roomPk(roomId)).build())
    ));

    List<AntRoomAssignment> out = new ArrayList<>();
    for (var page : res) {
      for (var e : page.items()) {
        out.add(fromEntity(e));
      }
    }
    return out;
  }

  @Override
  public AntRoomAssignment update(AntRoomAssignment assignment) {
    table.updateItem(toEntity(assignment));
    return assignment;
  }

  private static AntRoomAssignmentEntity toEntity(AntRoomAssignment a) {
    AntRoomAssignmentEntity e = new AntRoomAssignmentEntity();
    e.setPk(DynamoKeys.antPk(a.antId()));
    e.setSk(DynamoKeys.antRoomSk(a.roomId()));

    e.setRoomIdGSI(DynamoKeys.roomPk(a.roomId()));

    e.setAntId(a.antId());
    e.setRoomId(a.roomId());
    e.setCreatedAt(a.createdAt() != null ? a.createdAt().toString() : Instant.EPOCH.toString());
    e.setUpdatedAt(a.updatedAt() != null ? a.updatedAt().toString() : Instant.EPOCH.toString());
    e.setLastSeenMessageId(a.lastSeenMessageId());
    e.setLastRunAt(a.lastRunAt() != null ? a.lastRunAt().toString() : null);

    // role assignment
    e.setRoleId(a.roleId());
    e.setRoleName(a.roleName());

    // rolling summary
    e.setRoomSummary(a.roomSummary());
    e.setSummaryMsgCounter(a.summaryMsgCounter());

    return e;
  }

  private static AntRoomAssignment fromEntity(AntRoomAssignmentEntity e) {
    Instant createdAt = e.getCreatedAt() != null ? Instant.parse(e.getCreatedAt()) : Instant.EPOCH;
    Instant updatedAt = e.getUpdatedAt() != null ? Instant.parse(e.getUpdatedAt()) : Instant.EPOCH;
    Instant lastRunAt = e.getLastRunAt() != null ? Instant.parse(e.getLastRunAt()) : null;
    return new AntRoomAssignment(
        e.getAntId(),
        e.getRoomId(),
        createdAt,
        updatedAt,
        e.getLastSeenMessageId(),
        lastRunAt,
        e.getRoleId(),
        e.getRoleName(),
        e.getRoomSummary(),
        e.getSummaryMsgCounter() == null ? 0 : e.getSummaryMsgCounter()
    );
  }
}
