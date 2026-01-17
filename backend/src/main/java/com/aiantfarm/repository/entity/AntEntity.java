package com.aiantfarm.repository.entity;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import static com.aiantfarm.utils.DynamoIndexes.GSI_ANT_ID;


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
  // META#<antName>
  private String sk;

  // GSI partition key to query items by antId (Ant META, assignments, runs)
  private String antIdGSI;

  private String ownerUserId;
  private String name;
  private String model;
  private String personalityPrompt;
  private Integer intervalSeconds;
  private Boolean enabled;
  private Boolean replyEvenIfNoNew;
  private Integer maxMessagesPerWeek;
  private Integer messagesSentThisPeriod;
  private String periodStartDate;
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

  @DynamoDbSecondaryPartitionKey(indexNames = {GSI_ANT_ID})
  public String getAntIdGSI() { return antIdGSI; }
}
