package com.aiantfarm.store.dynamo;

import com.aiantfarm.domain.RoomMembership;
import com.aiantfarm.store.MembershipStore;
import com.aiantfarm.store.Page;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.aiantfarm.utils.DynamoHelper.*;

public class DynamoMembershipStore implements MembershipStore {
    private final DynamoDbClient db; private final String table;
    public DynamoMembershipStore(DynamoDbClient db, String tableName){ this.db=db; this.table=tableName; }

    private Map<String, AttributeValue> toItem(RoomMembership m){
        Map<String, AttributeValue> it=new HashMap<>();
        it.put("pk", dynamoString(m.id())); it.put("tenantId", dynamoString(m.tenantId()));
        it.put("roomId", dynamoString(m.roomId())); it.put("userId", dynamoString(m.userId()));
        it.put("role", dynamoString(m.role().name())); it.put("joinedAt", dynamoNum(toEpoch(m.joinedAt())));
        it.put("type", dynamoString("Membership")); return it;
    }
    private RoomMembership fromItem(Map<String, AttributeValue> it){
        return new RoomMembership(getDynamoString(it,"pk").orElseThrow(), getDynamoString(it,"tenantId").orElseThrow(),
                getDynamoString(it,"roomId").orElseThrow(), getDynamoString(it,"userId").orElseThrow(),
                com.aiantfarm.domain.RoomRole.valueOf(getDynamoString(it,"role").orElse("MEMBER")),
                fromEpoch(getDynamoNum(it,"joinedAt").orElse(0L)));
    }

    @Override public RoomMembership create(RoomMembership m){ db.putItem(PutItemRequest.builder().tableName(table).item(toItem(m)).build()); return m; }
    @Override public Optional<RoomMembership> findById(String tenant, String membershipId){
        GetItemResponse res=db.getItem(GetItemRequest.builder().tableName(table).key(Map.of("pk", dynamoString(membershipId))).build());
        if(!res.hasItem()) return Optional.empty(); RoomMembership m=fromItem(res.item());
        return tenant.equals(m.tenantId())?Optional.of(m):Optional.empty();
    }
    @Override public Page<RoomMembership> listByRoom(String tenant, String roomId, int limit, String token){
        ScanRequest.Builder b=ScanRequest.builder().tableName(table)
                .filterExpression("#t=:tenant AND #type=:type AND #r=:room")
                .expressionAttributeNames(Map.of("#t","tenantId","#type","type","#r","roomId"))
                .expressionAttributeValues(Map.of(":tenant", dynamoString(tenant), ":type", dynamoString("Membership"), ":room", dynamoString(roomId)))
                .limit(Math.max(limit,1));
        if(token!=null&&!token.isBlank()) b.exclusiveStartKey(Map.of("pk", dynamoString(token)));
        ScanResponse s=db.scan(b.build());
        List<RoomMembership> items=s.items().stream().map(this::fromItem)
                .sorted(Comparator.comparing(RoomMembership::joinedAt)).collect(Collectors.toList());
        String next=(s.lastEvaluatedKey()==null||s.lastEvaluatedKey().isEmpty())?null:s.lastEvaluatedKey().get("pk").s();
        return Page.of(items,next);
    }
    @Override public Page<RoomMembership> listByUser(String tenant, String userId, int limit, String token){
        ScanRequest.Builder b=ScanRequest.builder().tableName(table)
                .filterExpression("#t=:tenant AND #type=:type AND #u=:user")
                .expressionAttributeNames(Map.of("#t","tenantId","#type","type","#u","userId"))
                .expressionAttributeValues(Map.of(":tenant", dynamoString(tenant), ":type", dynamoString("Membership"), ":user", dynamoString(userId)))
                .limit(Math.max(limit,1));
        if(token!=null&&!token.isBlank()) b.exclusiveStartKey(Map.of("pk", dynamoString(token)));
        ScanResponse s=db.scan(b.build());
        List<RoomMembership> items=s.items().stream().map(this::fromItem)
                .sorted(Comparator.comparing(RoomMembership::joinedAt)).collect(Collectors.toList());
        String next=(s.lastEvaluatedKey()==null||s.lastEvaluatedKey().isEmpty())?null:s.lastEvaluatedKey().get("pk").s();
        return Page.of(items,next);
    }
    @Override public boolean delete(String tenant, String membershipId){
        db.deleteItem(DeleteItemRequest.builder().tableName(table).key(Map.of("pk", dynamoString(membershipId))).build()); return true;
    }
}
