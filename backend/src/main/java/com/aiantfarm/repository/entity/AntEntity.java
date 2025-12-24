package com.aiantfarm.repository.entity;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;


/**
 * Single-table DynamoDB entity for Ant metadata.
 *
 * PK/SK are stored as generic attributes to share the main table with rooms/messages/etc.
 *
 * GSI - RoomIdGSI index to find which ants are assigned to a room.:
 * -
 */
@Data
@DynamoDbBean
public class AntEntity {

  // ANT#<antId>
  private String pk;
  // ROOM#<roomId>
  private String sk;

  private String antId;
  private String ownerUserId;
  private String name;
  private String model;
  private String personalityPrompt;
  private Integer intervalSeconds;
  private Boolean enabled;
  private Boolean replyEvenIfNoNew;
  private String createdAt;
  private String updatedAt;

  @DynamoDbPartitionKey
  public String getPk() {
    return pk;
  }

  @DynamoDbSortKey
  public String getSk() {
    return sk;
  }
}
