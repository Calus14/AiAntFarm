package com.aiantfarm.repository.dynamo;

import com.aiantfarm.domain.AntRun;
import com.aiantfarm.domain.AntRunStatus;
import com.aiantfarm.repository.AntRunRepository;
import com.aiantfarm.repository.entity.AntRunEntity;
import com.aiantfarm.utils.DynamoKeys;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AntRunRepositoryImpl implements AntRunRepository {

  private final DynamoDbTable<AntRunEntity> table;

  public AntRunRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(AntRunEntity.class));
  }

  @Override
  public AntRun create(AntRun run) {
    table.putItem(toEntity(run));
    return run;
  }

  @Override
  public AntRun update(AntRun run) {
    table.updateItem(toEntity(run));
    return run;
  }

  @Override
  public List<AntRun> listByAnt(String antId, int limit) {
    if (antId == null || antId.isBlank()) return List.of();

    int pageSize = limit <= 0 ? 50 : limit;

    var res = table.query(r -> {
      r.queryConditional(QueryConditional.sortBeginsWith(
          Key.builder().partitionValue(DynamoKeys.antPk(antId)).sortValue("RUN#").build()));
      r.limit(pageSize);
      r.scanIndexForward(false);
    });

    List<AntRun> out = new ArrayList<>();
    for (var page : res) {
      for (var e : page.items()) {
        out.add(fromEntity(e));
      }
      break;
    }

    return out;
  }

  private static AntRunEntity toEntity(AntRun r) {
    AntRunEntity e = new AntRunEntity();
    e.setPk(DynamoKeys.antPk(r.antId()));
    e.setSk(DynamoKeys.antRunSk(r.startedAt(), r.id()));
    e.setRunId(r.id());
    e.setAntIdGSI(r.antId());
    e.setOwnerUserId(r.ownerUserId());
    e.setRoomId(r.roomId());
    e.setStartedAt(r.startedAt() != null ? r.startedAt().toString() : Instant.EPOCH.toString());
    e.setFinishedAt(r.finishedAt() != null ? r.finishedAt().toString() : null);
    e.setStatus(r.status() != null ? r.status().name() : AntRunStatus.RUNNING.name());
    e.setAntNotes(r.antNotes());
    e.setError(r.error());
    return e;
  }

  private static AntRun fromEntity(AntRunEntity e) {
    Instant startedAt = e.getStartedAt() != null ? Instant.parse(e.getStartedAt()) : Instant.EPOCH;
    Instant finishedAt = e.getFinishedAt() != null ? Instant.parse(e.getFinishedAt()) : null;
    AntRunStatus status = e.getStatus() != null ? AntRunStatus.valueOf(e.getStatus()) : AntRunStatus.RUNNING;

    return new AntRun(
        e.getRunId(),
        e.getAntIdGSI(),
        e.getOwnerUserId(),
        e.getRoomId(),
        startedAt,
        finishedAt,
        status,
        e.getAntNotes(),
        e.getError()
    );
  }
}

