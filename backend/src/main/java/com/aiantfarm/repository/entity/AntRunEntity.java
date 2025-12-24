package com.aiantfarm.repository.entity;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@DynamoDbBean
public class AntRunEntity {
  // ANT_RUN#<runId>
  private String pk;
  // RUN#<timestamp>#
  private String sk;

  private String runId;
  // GSI - AntId index to find runs by ant:
  private String antIdGSI;
  private String ownerUserId;
  private String roomId;

  private String startedAt;
  private String finishedAt;
  private String status;
  private String antNotes;
  private String error;

  @DynamoDbPartitionKey
  @DynamoDbAttribute("pk")
  public String getPk() { return pk; }

  @DynamoDbSortKey
  public String getSk() { return sk; }
}

