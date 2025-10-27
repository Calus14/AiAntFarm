package com.aiantfarm.store.dynamo;

import com.aiantfarm.domain.Message;
import com.aiantfarm.store.MessageStore;
import com.aiantfarm.store.Page;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.aiantfarm.utils.DynamoHelper.*;

public class DynamoMessageStore implements MessageStore {
    private final DynamoDbClient db; private final String table;
    public DynamoMessageStore(DynamoDbClient db, String tableName){ this.db=db; this.table=tableName; }

    private Map<String, AttributeValue> toItem(Message m){
        Map<String, AttributeValue> it=new HashMap<>();
        it.put("pk", dynamoString(m.id())); it.put("tenantId", dynamoString(m.tenantId())); it.put("roomId", dynamoString(m.roomId()));
        it.put("authorType", dynamoString(m.authorType().name())); if(m.authorUserId()!=null) it.put("authorUserId", dynamoString(m.authorUserId()));
        it.put("content", dynamoString(m.content())); it.put("createdAt", dynamoNum(toEpoch(m.createdAt()))); it.put("type", dynamoString("Message"));
        return it;
    }
    private Message fromItem(Map<String, AttributeValue> it){
        return new Message(getDynamoString(it,"pk").orElseThrow(), getDynamoString(it,"tenantId").orElseThrow(),
                getDynamoString(it,"roomId").orElseThrow(),
                com.aiantfarm.domain.AuthorType.valueOf(getDynamoString(it,"authorType").orElse("USER")),
                getDynamoString(it,"authorUserId").orElse(null), getDynamoString(it,"content").orElse(""),
                fromEpoch(getDynamoNum(it,"createdAt").orElse(0L)));
    }

    @Override public Message create(Message m){ db.putItem(PutItemRequest.builder().tableName(table).item(toItem(m)).build()); return m; }
    @Override public Optional<Message> findById(String tenant, String messageId){
        GetItemResponse res=db.getItem(GetItemRequest.builder().tableName(table).key(Map.of("pk", dynamoString(messageId))).build());
        if(!res.hasItem()) return Optional.empty(); Message m=fromItem(res.item());
        return tenant.equals(m.tenantId())?Optional.of(m):Optional.empty();
    }
    @Override public Page<Message> listByRoom(String tenant, String roomId, int limit, String token){
        ScanRequest.Builder b=ScanRequest.builder().tableName(table)
                .filterExpression("#t=:tenant AND #type=:type AND #r=:room")
                .expressionAttributeNames(Map.of("#t","tenantId","#type","type","#r","roomId"))
                .expressionAttributeValues(Map.of(":tenant", dynamoString(tenant), ":type", dynamoString("Message"), ":room", dynamoString(roomId)))
                .limit(Math.max(limit,1));
        if(token!=null&&!token.isBlank()) b.exclusiveStartKey(Map.of("pk", dynamoString(token)));
        ScanResponse s=db.scan(b.build());
        List<Message> items=s.items().stream().map(this::fromItem)
                .sorted(Comparator.comparing(Message::createdAt)).collect(Collectors.toList());
        String next=(s.lastEvaluatedKey()==null||s.lastEvaluatedKey().isEmpty())?null:s.lastEvaluatedKey().get("pk").s();
        return Page.of(items,next);
    }
    @Override public boolean delete(String tenant, String messageId){
        db.deleteItem(DeleteItemRequest.builder().tableName(table).key(Map.of("pk", dynamoString(messageId))).build()); return true;
    }
}
