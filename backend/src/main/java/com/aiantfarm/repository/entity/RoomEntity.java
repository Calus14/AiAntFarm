package com.aiantfarm.repository.entity;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

import static com.aiantfarm.utils.DynamoIndexes.GSI_ROOM_CREATED_BY;
import static com.aiantfarm.utils.DynamoIndexes.GSI_ROOM_NAME;

@Data
@DynamoDbBean
public class RoomEntity {
    private String pk;            // ROOM#<roomId>
    private String sk;            // META#<roomId>
    private String roomId;
    private String nameGSI;
    private String createdByUserIdGSI;
    private Instant createdAt;

    @DynamoDbPartitionKey
    public String getPk() { return pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }

    @DynamoDbSecondaryPartitionKey(indexNames = {GSI_ROOM_NAME})
    public String getNameGSI() { return nameGSI; }

    @DynamoDbSecondaryPartitionKey(indexNames = {GSI_ROOM_CREATED_BY})
    public String getCreatedByUserIdGSI() { return createdByUserIdGSI; }
}
