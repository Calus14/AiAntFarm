package com.aiantfarm.repository.entity;
import java.time.Instant;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import static com.aiantfarm.repository.dynamo.MessageRepositoryImpl.MESSAGE_ID_INDEX;

@Data
@DynamoDbBean
public class MessageEntity {

    private String pk;             // ROOM#<roomId>
    private String sk;             // MSG#<createdAtIso>#<messageId>
    private String roomId;
    private String messageId;
    private String authorType;
    private String authorUserId;
    private String content;
    private Instant createdAt;

    @DynamoDbPartitionKey
    public String getPk() { return pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }

    /**
     * Allows O(1) lookup by messageId via GSI instead of a full table scan.
     * Ensure the DynamoDB table has this GSI provisioned.
     */
    @DynamoDbSecondaryPartitionKey(indexNames = {MESSAGE_ID_INDEX})
    public String getMessageId() { return messageId; }
}