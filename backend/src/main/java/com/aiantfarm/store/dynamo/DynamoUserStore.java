
package com.aiantfarm.store.dynamo;

import com.aiantfarm.domain.User;
import com.aiantfarm.store.Page;
import com.aiantfarm.store.UserStore;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.aiantfarm.utils.DynamoHelper.*;

public class DynamoUserStore implements UserStore {
    private final DynamoDbClient db; private final String table;
    public DynamoUserStore(DynamoDbClient db, String tableName){ this.db=db; this.table=tableName; }

    private Map<String, AttributeValue> toItem(User u){
        Map<String, AttributeValue> m=new HashMap<>();
        m.put("pk", dynamoString(u.id())); m.put("tenantId", dynamoString(u.tenantId()));
        m.put("username", dynamoString(u.username())); m.put("displayName", dynamoString(u.displayName()));
        m.put("active", dyanmoBool(u.active())); m.put("createdAt", dynamoNum(toEpoch(u.createdAt())));
        m.put("type", dynamoString("User")); return m;
    }
    private User fromItem(Map<String, AttributeValue> it){
        return new User(getDynamoString(it,"pk").orElseThrow(), getDynamoString(it,"tenantId").orElseThrow(),
                getDynamoString(it,"username").orElse(""), getDynamoString(it,"displayName").orElse(""),
                fromEpoch(getDynamoNum(it,"createdAt").orElse(0L)), getDynamoBool(it,"active").orElse(true));
    }

    @Override public User create(User u){ db.putItem(PutItemRequest.builder().tableName(table).item(toItem(u)).build()); return u; }
    @Override public Optional<User> findById(String tenant, String userId){
        GetItemResponse r=db.getItem(GetItemRequest.builder().tableName(table).key(Map.of("pk", dynamoString(userId))).build());
        if(!r.hasItem()) return Optional.empty(); User u=fromItem(r.item());
        return tenant.equals(u.tenantId())?Optional.of(u):Optional.empty();
    }
    @Override public Page<User> listByTenant(String tenant, int limit, String token){
        ScanRequest.Builder b=ScanRequest.builder().tableName(table)
                .filterExpression("#t=:tenant AND #type=:type")
                .expressionAttributeNames(Map.of("#t","tenantId","#type","type"))
                .expressionAttributeValues(Map.of(":tenant", dynamoString(tenant), ":type", dynamoString("User")))
                .limit(Math.max(limit,1));
        if(token!=null&&!token.isBlank()) b.exclusiveStartKey(Map.of("pk", dynamoString(token)));
        ScanResponse s=db.scan(b.build());
        List<User> items=s.items().stream().map(this::fromItem)
                .sorted(Comparator.comparing(User::createdAt)).collect(Collectors.toList());
        String next=(s.lastEvaluatedKey()==null||s.lastEvaluatedKey().isEmpty())?null:s.lastEvaluatedKey().get("pk").s();
        return Page.of(items,next);
    }
    @Override public User update(User u){ return create(u); }
    @Override public boolean delete(String tenant, String userId){
        db.deleteItem(DeleteItemRequest.builder().tableName(table).key(Map.of("pk", dynamoString(userId))).build()); return true;
    }
}
