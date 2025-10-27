package com.aiantfarm.store.dynamo;

import com.aiantfarm.domain.Room;
import com.aiantfarm.store.Page;
import com.aiantfarm.store.RoomStore;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.aiantfarm.utils.DynamoHelper.*;

public class DynamoRoomStore implements RoomStore {
    private final DynamoDbClient db; private final String table;
    public DynamoRoomStore(DynamoDbClient db, String tableName){ this.db=db; this.table=tableName; }

    private Map<String, AttributeValue> toItem(Room r){
        Map<String, AttributeValue> m=new HashMap<>();
        m.put("pk", dynamoString(r.id())); m.put("tenantId", dynamoString(r.tenantId())); m.put("name", dynamoString(r.name()));
        if(r.createdByUserId()!=null) m.put("createdByUserId", dynamoString(r.createdByUserId()));
        m.put("createdAt", dynamoNum(toEpoch(r.createdAt()))); m.put("type", dynamoString("Room")); return m;
    }
    private Room fromItem(Map<String, AttributeValue> it){
        return new Room(getDynamoString(it,"pk").orElseThrow(), getDynamoString(it,"tenantId").orElseThrow(),
                getDynamoString(it,"name").orElse(""), getDynamoString(it,"createdByUserId").orElse(null),
                fromEpoch(getDynamoNum(it,"createdAt").orElse(0L)));
    }

    @Override public Room create(Room r){ db.putItem(PutItemRequest.builder().tableName(table).item(toItem(r)).build()); return r; }
    @Override public Optional<Room> findById(String tenant, String roomId){
        GetItemResponse res=db.getItem(GetItemRequest.builder().tableName(table).key(Map.of("pk", dynamoString(roomId))).build());
        if(!res.hasItem()) return Optional.empty(); Room r=fromItem(res.item());
        return tenant.equals(r.tenantId())?Optional.of(r):Optional.empty();
    }
    @Override public Optional<Room> findByName(String tenant, String name){
        ScanResponse s=db.scan(ScanRequest.builder().tableName(table)
                .filterExpression("#t=:tenant AND #type=:type AND #n=:name")
                .expressionAttributeNames(Map.of("#t","tenantId","#type","type","#n","name"))
                .expressionAttributeValues(Map.of(":tenant", dynamoString(tenant), ":type", dynamoString("Room"), ":name", dynamoString(name)))
                .limit(1).build());
        if(!s.hasItems()||s.items().isEmpty()) return Optional.empty();
        return Optional.of(fromItem(s.items().get(0)));
    }
    @Override public Page<Room> listByTenant(String tenant, int limit, String token){
        ScanRequest.Builder b=ScanRequest.builder().tableName(table)
                .filterExpression("#t=:tenant AND #type=:type")
                .expressionAttributeNames(Map.of("#t","tenantId","#type","type"))
                .expressionAttributeValues(Map.of(":tenant", dynamoString(tenant), ":type", dynamoString("Room")))
                .limit(Math.max(limit,1));
        if(token!=null&&!token.isBlank()) b.exclusiveStartKey(Map.of("pk", dynamoString(token)));
        ScanResponse s=db.scan(b.build());
        List<Room> items=s.items().stream().map(this::fromItem)
                .sorted(Comparator.comparing(Room::createdAt)).collect(Collectors.toList());
        String next=(s.lastEvaluatedKey()==null||s.lastEvaluatedKey().isEmpty())?null:s.lastEvaluatedKey().get("pk").s();
        return Page.of(items,next);
    }
    @Override public Room update(Room r){ return create(r); }
    @Override public boolean delete(String tenant, String roomId){
        db.deleteItem(DeleteItemRequest.builder().tableName(table).key(Map.of("pk", dynamoString(roomId))).build()); return true;
    }
}
