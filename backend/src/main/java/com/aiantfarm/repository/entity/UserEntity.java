package com.aiantfarm.repository.entity;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

@Data
@DynamoDbBean
public class UserEntity {
    private String pk;            // USER#<userId>
    private String sk;            // PROFILE#<userId>
    private String displayName;
    private String userEmail;
    private boolean active;
    private Instant createdAt;
    private Integer antLimit;
    private Integer antRoomLimit;
    private Integer roomLimit;

    @DynamoDbPartitionKey
    public String getPk() { return pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
}