package com.aiantfarm.repository.dynamo;

import com.aiantfarm.domain.Ant;
import com.aiantfarm.repository.AntRepository;
import com.aiantfarm.repository.entity.AntEntity;
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

public class AntRepositoryImpl implements AntRepository {

  private final DynamoDbTable<AntEntity> table;
  private final DynamoDbIndex<AntEntity> ownerIndex;

  private static final String OWNER_INDEX = "gsi1";

  public AntRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(AntEntity.class));
    this.ownerIndex = table.index(OWNER_INDEX);
  }

  @Override
  public Ant create(Ant ant) {
    table.putItem(toEntity(ant));
    return ant;
  }

  @Override
  public Ant update(Ant ant) {
    table.updateItem(toEntity(ant));
    return ant;
  }

  @Override
  public Optional<Ant> findById(String antId) {
    if (antId == null || antId.isBlank()) return Optional.empty();

    AntEntity e = table.getItem(r -> r.key(Key.builder()
        .partitionValue(DynamoKeys.antPk(antId))
        .sortValue(DynamoKeys.antMetaSk(antId))
        .build()));

    return Optional.ofNullable(e).map(AntRepositoryImpl::fromEntity);
  }

  @Override
  public List<Ant> listByOwnerUserId(String ownerUserId) {
    if (ownerUserId == null || ownerUserId.isBlank()) return List.of();

    var res = ownerIndex.query(r -> r.queryConditional(
        QueryConditional.keyEqualTo(Key.builder().partitionValue(DynamoKeys.antOwnerGsiPk(ownerUserId)).build())
    ));

    List<Ant> out = new ArrayList<>();
    for (var page : res) {
      for (var e : page.items()) {
        // Only meta items.
        if (e.getSk() != null && e.getSk().startsWith("META#")) {
          out.add(fromEntity(e));
        }
      }
    }
    return out;
  }

  private static AntEntity toEntity(Ant a) {
    AntEntity e = new AntEntity();
    e.setPk(DynamoKeys.antPk(a.id()));
    e.setSk(DynamoKeys.antMetaSk(a.id()));

    e.setAntId(a.id());
    e.setOwnerUserId(a.ownerUserId());
    e.setName(a.name());
    e.setPersonalityPrompt(a.personalityPrompt());
    e.setIntervalSeconds(a.intervalSeconds());
    e.setEnabled(a.enabled());
    e.setReplyEvenIfNoNew(a.replyEvenIfNoNew());
    e.setCreatedAt(a.createdAt() != null ? a.createdAt().toString() : Instant.EPOCH.toString());
    e.setUpdatedAt(a.updatedAt() != null ? a.updatedAt().toString() : Instant.EPOCH.toString());
    return e;
  }

  private static Ant fromEntity(AntEntity e) {
    Instant createdAt = e.getCreatedAt() != null ? Instant.parse(e.getCreatedAt()) : Instant.EPOCH;
    Instant updatedAt = e.getUpdatedAt() != null ? Instant.parse(e.getUpdatedAt()) : Instant.EPOCH;
    return new Ant(
        e.getAntId(),
        e.getOwnerUserId(),
        e.getName(),
        e.getPersonalityPrompt() == null ? "" : e.getPersonalityPrompt(),
        e.getIntervalSeconds() != null ? e.getIntervalSeconds() : 60,
        e.getEnabled() != null && e.getEnabled(),
        e.getReplyEvenIfNoNew() != null && e.getReplyEvenIfNoNew(),
        createdAt,
        updatedAt
    );
  }
}

