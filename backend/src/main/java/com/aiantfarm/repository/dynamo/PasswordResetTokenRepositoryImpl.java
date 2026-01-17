package com.aiantfarm.repository.dynamo;

import com.aiantfarm.repository.PasswordResetTokenRepository;
import com.aiantfarm.repository.entity.PasswordResetTokenEntity;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

/**
 * DynamoDB implementation of the token repository.
 * <p>
 * Why: Handles the actual database operations for the token entity.
 * <p>
 * What: Saves new tokens and provides a method to mark a token as used.
 */
public class PasswordResetTokenRepositoryImpl implements PasswordResetTokenRepository {

  private final DynamoDbTable<PasswordResetTokenEntity> table;

  public PasswordResetTokenRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(PasswordResetTokenEntity.class));
  }

  private String getPk(String tokenId) {
    return "TOKEN#" + tokenId;
  }
  private String getSk() {
    return "META";
  }

  @Override
  public void save(PasswordResetTokenEntity entity) {
    if (entity.getTokenId() == null) throw new IllegalArgumentException("Token ID required");
    entity.setPk(getPk(entity.getTokenId()));
    entity.setSk(getSk());
    table.putItem(entity);
  }

  @Override
  public Optional<PasswordResetTokenEntity> findByTokenId(String tokenId) {
    return Optional.ofNullable(table.getItem(Key.builder()
        .partitionValue(getPk(tokenId))
        .sortValue(getSk())
        .build()));
  }

  @Override
  public void markUsed(String tokenId) {
    var entity = findByTokenId(tokenId).orElse(null);
    if (entity != null) {
      entity.setUsed(true);
      table.putItem(entity);
    }
  }
}
