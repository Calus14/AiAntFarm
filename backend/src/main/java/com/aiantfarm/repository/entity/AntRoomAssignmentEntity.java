package com.aiantfarm.repository.entity;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import static com.aiantfarm.utils.DynamoIndexes.GSI_ROOM_ID;

@Data
@DynamoDbBean
public class AntRoomAssignmentEntity {
  private String pk;
  private String sk;

  // GSI_ROOM_ID: room -> ants
  private String roomIdGSI;

  private String antId;
  private String roomId;
  private String createdAt;
  private String updatedAt;
  private String lastSeenMessageId;
  private String lastRunAt;

  // RoomAntRole assignment fields
  private String roleId;
  private String roleName;

  // Rolling summary fields (internal-only; not exposed via API)
  private String roomSummary;
  private Integer summaryMsgCounter;

  @DynamoDbPartitionKey
  public String getPk() { return pk; }

  @DynamoDbSortKey
  public String getSk() { return sk; }

  @DynamoDbSecondaryPartitionKey(indexNames = {GSI_ROOM_ID})
  public String getRoomIdGSI() { return roomIdGSI; }
}
