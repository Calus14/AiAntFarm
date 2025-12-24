package com.aiantfarm.repository.dynamo;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import com.aiantfarm.repository.AntRepository;
import com.aiantfarm.repository.entity.AntEntity;
import com.aiantfarm.utils.DynamoKeys;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AntRepositoryImpl implements AntRepository {

  private final DynamoDbTable<AntEntity> table;


  public AntRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(AntEntity.class));
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

    // NOTE: This is intentionally a full table scan (no owner GSI) for MVP simplicity.
    // If this becomes hot, add a GSI on ownerUserId.
    var pages = table.scan();

    List<Ant> out = new ArrayList<>();
    for (var page : pages) {
      for (var e : page.items()) {
        if (e == null) continue;

        // Only include Ant META items (exclude AntRoomAssignment, AntRun, etc.)
        if (e.getSk() == null || !e.getSk().startsWith("META#")) continue;

        if (ownerUserId.equals(e.getOwnerUserId())) {
          out.add(fromEntity(e));
        }
      }
    }

    return out;
  }

  @Override
  public List<Ant> listAll() {
    var pages = table.scan();

    List<Ant> out = new ArrayList<>();
    for (var page : pages) {
      for (var e : page.items()) {
        if (e == null) continue;
        if (e.getSk() == null || !e.getSk().startsWith("META#")) continue;
        out.add(fromEntity(e));
      }
    }

    return out;
  }

  private static AntEntity toEntity(Ant a) {
    AntEntity e = new AntEntity();
    e.setPk(DynamoKeys.antPk(a.id()));
    e.setSk(DynamoKeys.antMetaSk(a.name()));
    e.setAntId(a.id());
    e.setOwnerUserId(a.ownerUserId());
    e.setName(a.name());
    e.setModel((a.model() == null ? AiModel.MOCK : a.model()).name());
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

    AiModel model;
    try {
      model = e.getModel() == null || e.getModel().isBlank() ? AiModel.MOCK : AiModel.valueOf(e.getModel());
    } catch (Exception ex) {
      model = AiModel.MOCK;
    }

    return new Ant(
        e.getAntId(),
        e.getOwnerUserId(),
        e.getName(),
        model,
        e.getPersonalityPrompt() == null ? "" : e.getPersonalityPrompt(),
        e.getIntervalSeconds() != null ? e.getIntervalSeconds() : 60,
        e.getEnabled() != null && e.getEnabled(),
        e.getReplyEvenIfNoNew() != null && e.getReplyEvenIfNoNew(),
        createdAt,
        updatedAt
    );
  }
}
