package com.aiantfarm.repository.entity;

import com.aiantfarm.utils.DynamoKeys;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

/**
 * DynamoDB entity for storing password reset token metadata.
 * <p>
 * Why: Defines the data structure for storing token metadata in DynamoDB to prevent replay attacks.
 * <p>
 * What: Stores the Token ID (jti), expiration, and a used boolean flag.
 */
@Data
@NoArgsConstructor
@DynamoDbBean
public class PasswordResetTokenEntity {
  private String pk;
  private String sk;

  private String tokenId; // jti
  private String email;
  private String purpose; // reset | verify
  private boolean used;
  private Instant expiresAt;
  private Long ttl; // DynamoDB TTL attribute

  @DynamoDbPartitionKey
  public String getPk() { return pk; }

  @DynamoDbSortKey
  public String getSk() { return sk; }
}
