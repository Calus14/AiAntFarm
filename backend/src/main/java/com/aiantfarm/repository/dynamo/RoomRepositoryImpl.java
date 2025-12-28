package com.aiantfarm.repository.dynamo;

import com.aiantfarm.domain.Room;
import com.aiantfarm.repository.Page;
import com.aiantfarm.repository.RoomRepository;
import com.aiantfarm.repository.entity.RoomEntity;
import com.aiantfarm.utils.DynamoKeys;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RoomRepositoryImpl implements RoomRepository {

  private final DynamoDbTable<RoomEntity> table;
  private final DynamoDbIndex<RoomEntity> roomNameIndex;
  private final DynamoDbIndex<RoomEntity> createdByIndex;

  /**
   * GSI names are configured on the RoomEntity getters via annotations.
   */
  private static final String ROOM_NAME_INDEX = "GSI_ROOM_NAME";
  private static final String CREATED_BY_INDEX = "GSI_ROOM_CREATED_BY";

  public RoomRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(RoomEntity.class));
    this.roomNameIndex = table.index(ROOM_NAME_INDEX);
    this.createdByIndex = table.index(CREATED_BY_INDEX);
  }

  @Override
  public Room create(Room room) {
    table.putItem(toEntity(room));
    return room;
  }

  @Override
  public Optional<Room> findById(String roomId) {
    if (roomId == null || roomId.isBlank()) return Optional.empty();

    RoomEntity e = table.getItem(r -> r.key(Key.builder()
        .partitionValue(DynamoKeys.roomPk(roomId))
        .sortValue(DynamoKeys.roomMetaSk(roomId))
        .build()));

    return Optional.ofNullable(e).map(RoomRepositoryImpl::fromEntity);
  }

  @Override
  public Optional<Room> findByName(String name) {
    if (name == null || name.isBlank()) return Optional.empty();

    var res = roomNameIndex.query(r -> r.queryConditional(
        QueryConditional.keyEqualTo(Key.builder().partitionValue(name).build())
    ));

    for (var page : res) {
      for (var item : page.items()) {
        return Optional.of(fromEntity(item));
      }
    }

    return Optional.empty();
  }

  @Override
  public Page<Room> listAll(int limit, String nextToken) {
    // Scan operation to list all rooms - inefficient for large tables but acceptable for MVP.
    // IMPORTANT: Dynamo scans paginate by *scanned* items, not filtered matches.
    // If the table contains other entity types (e.g., ant runs), we must scan forward until
    // we have collected `limit` rooms or we exhaust the scan.

    int pageSize = (limit <= 0 || limit > 200) ? 50 : limit;

    Map<String, AttributeValue> exclusiveStartKey = decodeScanToken(nextToken);

    List<Room> items = new ArrayList<>();
    String outNext = null;

    // Keep scanning until we've collected enough rooms (or we run out of data).
    while (items.size() < pageSize) {
      int remaining = pageSize - items.size();

      Map<String, AttributeValue> finalExclusiveStartKey = exclusiveStartKey;
      var scan = table.scan(r -> {
        // limit applies to scanned items; use remaining to reduce extra work.
        r.limit(remaining);
        if (finalExclusiveStartKey != null && !finalExclusiveStartKey.isEmpty()) {
          r.exclusiveStartKey(finalExclusiveStartKey);
        }
      });

      Map<String, AttributeValue> lastEvaluatedKey = null;

      for (var page : scan) {
        for (var e : page.items()) {
          // Guard nulls in shared tables.
          if (e.getPk() == null || e.getSk() == null) continue;

          // Filter only room meta items if table is shared.
          if (e.getPk().startsWith("ROOM#") && e.getSk().startsWith("META#")) {
            items.add(fromEntity(e));
            if (items.size() >= pageSize) break;
          }
        }

        lastEvaluatedKey = page.lastEvaluatedKey();
        break; // we only requested one page; we'll loop manually if needed
      }

      if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
        // End of scan.
        outNext = null;
        break;
      }

      // More data exists. If we haven't filled the page, continue scanning from this key.
      exclusiveStartKey = lastEvaluatedKey;
      outNext = encodeScanToken(lastEvaluatedKey);
    }

    return new Page<>(items, outNext);
  }

  private static String encodeScanToken(Map<String, AttributeValue> lastEvaluatedKey) {
    if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) return null;
    AttributeValue pk = lastEvaluatedKey.get("pk");
    AttributeValue sk = lastEvaluatedKey.get("sk");
    if (pk == null || pk.s() == null || sk == null || sk.s() == null) return null;

    String raw = pk.s() + "|" + sk.s();
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, AttributeValue> decodeScanToken(String token) {
    if (token == null || token.isBlank()) return null;
    try {
      byte[] bytes = Base64.getUrlDecoder().decode(token);
      String raw = new String(bytes, StandardCharsets.UTF_8);
      int idx = raw.indexOf('|');
      if (idx <= 0) return null;
      String pk = raw.substring(0, idx);
      String sk = raw.substring(idx + 1);
      if (pk.isBlank() || sk.isBlank()) return null;

      return Map.of(
          "pk", AttributeValue.builder().s(pk).build(),
          "sk", AttributeValue.builder().s(sk).build()
      );
    } catch (Exception e) {
      // Treat bad tokens as "start from beginning".
      return null;
    }
  }

  @Override
  public Page<Room> listByUserCreatedId(String userId, int limit, String nextToken) {
    if (userId == null || userId.isBlank()) return new Page<>(List.of(), null);

    int pageSize = limit <= 0 ? 50 : limit;

    var query = createdByIndex.query(r -> {
      r.queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()));
      r.limit(pageSize);
      if (nextToken != null && !nextToken.isBlank()) {
        // nextToken is last evaluated roomId
        String lastPk = DynamoKeys.roomPk(nextToken);
        String lastSk = DynamoKeys.roomMetaSk(nextToken);
        r.exclusiveStartKey(Map.of(
            "pk", AttributeValue.builder().s(lastPk).build(),
            "sk", AttributeValue.builder().s(lastSk).build()
        ));
      }
    });

    List<Room> items = new ArrayList<>();
    String outNext = null;

    for (var page : query) {
      for (var e : page.items()) {
        items.add(fromEntity(e));
      }
      if (page.lastEvaluatedKey() != null && page.lastEvaluatedKey().get("pk") != null) {
        String pk = page.lastEvaluatedKey().get("pk").s();
        if (pk != null && pk.startsWith("ROOM#")) {
          outNext = pk.substring("ROOM#".length());
        }
      }
      break;
    }

    return new Page<>(items, outNext);
  }

  @Override
  public Room update(Room room) {
    table.updateItem(toEntity(room));
    return room;
  }

  @Override
  public boolean deleteByRoomId(String roomId) {
    if (roomId == null || roomId.isBlank()) return false;
    table.deleteItem(r -> r.key(Key.builder()
        .partitionValue(DynamoKeys.roomPk(roomId))
        .sortValue(DynamoKeys.roomMetaSk(roomId))
        .build()));
    return true;
  }

  private static RoomEntity toEntity(Room r) {
    RoomEntity e = new RoomEntity();
    e.setPk(DynamoKeys.roomPk(r.id()));
    e.setSk(DynamoKeys.roomMetaSk(r.id()));
    e.setRoomId(r.id());
    e.setNameGSI(r.name());
    e.setCreatedByUserIdGSI(r.createdByUserId());
    e.setScenarioText(r.scenarioText());
    e.setCreatedAt(r.createdAt());
    return e;
  }

  private static Room fromEntity(RoomEntity e) {
    String roomId = e.getRoomId();
    if (roomId == null && e.getPk() != null && e.getPk().startsWith("ROOM#")) {
      roomId = e.getPk().substring("ROOM#".length());
    }
    Instant createdAt = e.getCreatedAt() != null ? e.getCreatedAt() : Instant.EPOCH;
    String scenarioText = e.getScenarioText() == null ? "" : e.getScenarioText();
    return new Room(roomId, e.getNameGSI(), e.getCreatedByUserIdGSI(), scenarioText, createdAt);
  }
}

