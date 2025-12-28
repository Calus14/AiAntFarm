package com.aiantfarm.repository.entity;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

@Data
@DynamoDbBean
public class RoomAntRoleEntity {
  private String pk; // ROOM#<roomId>
  private String sk; // ANTROLE#<roleId>

  private String roomId;
  private String roleId;

  private String name;
  private String prompt;
  private Integer maxSpots;

  private Instant createdAt;
  private Instant updatedAt;

  @DynamoDbPartitionKey
  public String getPk() { return pk; }

  @DynamoDbSortKey
  public String getSk() { return sk; }
}

