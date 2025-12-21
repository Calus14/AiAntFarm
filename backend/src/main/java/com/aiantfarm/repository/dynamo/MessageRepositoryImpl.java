package com.aiantfarm.repository.dynamo;

import com.aiantfarm.domain.AuthorType;
import com.aiantfarm.domain.Message;
import com.aiantfarm.repository.MessageRepository;
import com.aiantfarm.repository.Page;
import com.aiantfarm.repository.entity.MessageEntity;
import com.aiantfarm.utils.DynamoKeys;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MessageRepositoryImpl implements MessageRepository {

  private final DynamoDbTable<MessageEntity> table;
  private final DynamoDbIndex<MessageEntity> messageIdIndex;

  public static final String MESSAGE_ID_INDEX = "GSI_MESSAGE_ID";

  public MessageRepositoryImpl(DynamoDbEnhancedClient enhancedClient, String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(MessageEntity.class));
    this.messageIdIndex = table.index(MESSAGE_ID_INDEX);
  }

  private static MessageEntity toEntity(Message m) {
    MessageEntity e = new MessageEntity();
    e.setPk(DynamoKeys.roomPk(m.roomId()));
    e.setSk(DynamoKeys.messageSk(m.createdAt(), m.id()));
    e.setRoomId(m.roomId());
    e.setMessageId(m.id());
    e.setAuthorType(m.authorType() != null ? m.authorType().name() : AuthorType.USER.name());
    e.setAuthorUserId(m.authorUserId());
    e.setContent(m.content());
    e.setCreatedAt(m.createdAt());
    return e;
  }

  private static Message fromEntity(MessageEntity e) {
    return new Message(
        e.getMessageId(),
        e.getRoomId(),
        e.getAuthorType() != null ? AuthorType.valueOf(e.getAuthorType()) : AuthorType.USER,
        e.getAuthorUserId(),
        e.getContent(),
        e.getCreatedAt() != null ? e.getCreatedAt() : Instant.EPOCH
    );
  }

  @Override
  public Message create(Message message) {
    table.putItem(toEntity(message));
    return message;
  }

  @Override
  public Optional<Message> findById(String messageId) {
    if (messageId == null || messageId.isBlank()) return Optional.empty();

    var res = messageIdIndex.query(r -> r.queryConditional(
        QueryConditional.keyEqualTo(Key.builder().partitionValue(messageId).build())
    ));

    for (var page : res) {
      for (var e : page.items()) {
        return Optional.of(fromEntity(e));
      }
    }

    return Optional.empty();
  }

  @Override
  public Page<Message> listByRoom(String roomId, int limit, String nextToken) {
    if (roomId == null || roomId.isBlank()) {
      return new Page<>(List.of(), null);
    }

    int pageSize = limit <= 0 ? 50 : limit;
    final String pk = DynamoKeys.roomPk(roomId);
    final String msgPrefix = "MSG#";

    var req = table.query(r -> {
      r.queryConditional(QueryConditional.sortBeginsWith(
          Key.builder().partitionValue(pk).sortValue(msgPrefix).build()));
      r.limit(pageSize);
      // newest first (SK contains ISO timestamp prefix)
      r.scanIndexForward(false);

      if (nextToken != null && !nextToken.isBlank()) {
        r.exclusiveStartKey(Map.of(
            "pk", AttributeValue.builder().s(pk).build(),
            "sk", AttributeValue.builder().s(nextToken).build()
        ));
      }
    });

    List<Message> items = new ArrayList<>();
    String outNext = null;
    for (var page : req) {
      for (var e : page.items()) {
        items.add(fromEntity(e));
      }
      if (page.lastEvaluatedKey() != null && page.lastEvaluatedKey().get("sk") != null) {
        outNext = page.lastEvaluatedKey().get("sk").s();
      }
      break; // single page
    }

    return new Page<>(items, outNext);
  }

  @Override
  public boolean delete(String messageId) {
    if (messageId == null || messageId.isBlank()) return false;

    // Find the entity so we know its pk/sk.
    MessageEntity found = null;

    var res = messageIdIndex.query(r -> r.queryConditional(
        QueryConditional.keyEqualTo(Key.builder().partitionValue(messageId).build())
    ));

    outer:
    for (var page : res) {
      for (var e : page.items()) {
        found = e;
        break outer;
      }
    }

    if (found == null) return false;

    final MessageEntity existing = found;

    boolean isSystem = "SYSTEM".equalsIgnoreCase(existing.getAuthorType());
    String roomId = existing.getRoomId();

    table.deleteItem(r -> r.key(Key.builder()
        .partitionValue(existing.getPk())
        .sortValue(existing.getSk())
        .build()));

    if (!isSystem && roomId != null && !roomId.isBlank()) {
      String content = "Msg Deleted - " + Instant.now().toString();
      Message sys = Message.createSystemMsg(roomId, content);
      table.putItem(toEntity(sys));
    }

    return true;
  }
}
