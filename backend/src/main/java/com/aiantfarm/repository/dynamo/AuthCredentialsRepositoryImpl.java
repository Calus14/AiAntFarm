package com.aiantfarm.repository.dynamo;

import com.aiantfarm.repository.AuthCredentialsRepository;
import com.aiantfarm.repository.entity.AuthCredentialsEntity;
import com.aiantfarm.utils.DynamoKeys;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Optional;

public class AuthCredentialsRepositoryImpl implements AuthCredentialsRepository {

  private final DynamoDbTable<AuthCredentialsEntity> table;
  private final DynamoDbIndex<AuthCredentialsEntity> userEmailIndex;

  private static final String EMAIL_INDEX = "GSI_Email";

  public AuthCredentialsRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(AuthCredentialsEntity.class));
    this.userEmailIndex = table.index(EMAIL_INDEX);
  }

  private static AuthCredentialsEntity withKeys(AuthCredentialsEntity entity, String userId) {
    entity.setPk(DynamoKeys.userPk(userId));
    entity.setSk(DynamoKeys.credSk());
    entity.setUserId(userId);
    return entity;
  }

  @Override
  public AuthCredentialsEntity create(AuthCredentialsEntity entity) {
    if (entity == null || entity.getUserId() == null || entity.getUserId().isBlank()) {
      throw new IllegalArgumentException("userId must be set on AuthCredentialsEntity before create");
    }
    if (entity.getEmailGSI() == null || entity.getEmailGSI().isBlank()) {
      throw new IllegalArgumentException("email must be set on AuthCredentialsEntity before create");
    }
    withKeys(entity, entity.getUserId());
    table.putItem(entity);
    return entity;
  }

  @Override
  public Optional<AuthCredentialsEntity> findByUserId(String userId) {
    if (userId == null || userId.isBlank()) return Optional.empty();
    AuthCredentialsEntity e = table.getItem(r ->
        r.key(Key.builder()
            .partitionValue(DynamoKeys.userPk(userId))
            .sortValue(DynamoKeys.credSk())
            .build())
    );
    return Optional.ofNullable(e);
  }

  @Override
  public Optional<AuthCredentialsEntity> findByEmail(String email) {
    if (email == null || email.isBlank()) return Optional.empty();
    var results = userEmailIndex.query(r -> r.queryConditional(
        QueryConditional.keyEqualTo(Key.builder().partitionValue(email.toLowerCase()).build())
    ));
    for (var page : results) {
      for (var item : page.items()) {
        return Optional.of(item);
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean deleteByUserId(String userId) {
    if (userId == null || userId.isBlank()) return false;
    table.deleteItem(r ->
        r.key(Key.builder()
            .partitionValue(DynamoKeys.userPk(userId))
            .sortValue(DynamoKeys.credSk())
            .build())
    );
    return true;
  }
}
