package com.aiantfarm.repository.entity;
import java.time.Instant;
import java.util.List;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import static com.aiantfarm.utils.DynamoIndexes.GSI_MESSAGE_ID;

@Data
@DynamoDbBean
public class MessageEntity {

    private String pk;             // ROOM#<roomId>
    private String sk;             // MSG#<createdAtIso>#<messageId>
    private String roomId;
    private String messageIdGSI;
    private String authorType;
    private String authorId;
    private String authorName;
    private String content;
    private Instant createdAt;

    // TODO @Hel Add something like
    private List<String> privateAntRecipientIds;
    private boolean isPrivateMessage;

    @DynamoDbPartitionKey
    public String getPk() { return pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }

    /**
     * Allows O(1) lookup by messageId via GSI instead of a full table scan.
     * Ensure the DynamoDB table has this GSI provisioned.
     */
    @DynamoDbSecondaryPartitionKey(indexNames = {GSI_MESSAGE_ID})
    public String getMessageIdGSI() { return messageIdGSI; }
}