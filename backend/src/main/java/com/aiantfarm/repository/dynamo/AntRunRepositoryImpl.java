package com.aiantfarm.repository.dynamo;

import com.aiantfarm.domain.AntRun;
import com.aiantfarm.domain.AntRunStatus;
import com.aiantfarm.repository.AntRunRepository;
import com.aiantfarm.repository.entity.AntRunEntity;
import com.aiantfarm.utils.DynamoKeys;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.aiantfarm.utils.DynamoIndexes.GSI_ANT_ID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AntRunRepositoryImpl implements AntRunRepository {

  private final DynamoDbTable<AntRunEntity> table;
  private final DynamoDbIndex<AntRunEntity> antIndex;

  private static final long DEFAULT_TTL_DAYS = 3;

  public AntRunRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(AntRunEntity.class));
    this.antIndex = table.index(GSI_ANT_ID);
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
  public void deleteAllByAnt(String antId) {
    if (antId == null || antId.isBlank()) return;

    // Best-effort list+delete using GSI.
    var res = antIndex.query(r -> {
      r.queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(antId).build()));
      r.limit(500);
      r.scanIndexForward(false);
    });

    for (var page : res) {
      for (var e : page.items()) {
        if (e == null) continue;
        try {
          table.deleteItem(r -> r.key(Key.builder()
              .partitionValue(e.getPk())
              .sortValue(e.getSk())
              .build()));
        } catch (Exception ex) {
          log.warn("Failed to delete AntRun item during clearRuns antId={} pk={} sk={}", antId, e.getPk(), e.getSk(), ex);
        }
      }
      // one page is usually enough; user can repeat if huge.
      break;
    }
  }

  @Override
  public List<AntRun> listByAnt(String antId, int limit) {
    if (antId == null || antId.isBlank()) return List.of();

    int pageSize = limit <= 0 ? 50 : limit;

    // Query the GSI by antId, then filter to RUN items (sk starts with RUN#)
    var res = antIndex.query(r -> {
      r.queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(antId).build()));
      r.limit(pageSize);
      r.scanIndexForward(false);
    });

    List<AntRun> out = new ArrayList<>();
    for (var page : res) {
      for (var e : page.items()) {
        if (e == null) continue;
        if (e.getSk() == null || !e.getSk().startsWith("RUN#")) continue;
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

    // TTL: default 3 days after start.
    Instant started = r.startedAt() != null ? r.startedAt() : Instant.now();
    long ttlSeconds = started.plus(DEFAULT_TTL_DAYS, ChronoUnit.DAYS).getEpochSecond();
    e.setTtlEpochSeconds(ttlSeconds);

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
