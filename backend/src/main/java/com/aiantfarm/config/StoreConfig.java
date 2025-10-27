// src/main/java/com/aiantfarm/config/StoreConfig.java
package com.aiantfarm.config;

import com.aiantfarm.store.*;
import com.aiantfarm.store.dynamo.*;
import com.aiantfarm.store.inmemory.InMemoryMembershipStore;
import com.aiantfarm.store.inmemory.InMemoryMessageStore;
import com.aiantfarm.store.inmemory.InMemoryRoomStore;
import com.aiantfarm.store.inmemory.InMemoryUserStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class StoreConfig {

    // --- Local (in-memory) ---
    @Profile("local")
    @Bean UserStore userStoreLocal() { return new InMemoryUserStore(); }
    @Profile("local")
    @Bean RoomStore roomStoreLocal() { return new InMemoryRoomStore(); }
    @Profile("local")
    @Bean MessageStore messageStoreLocal() { return new InMemoryMessageStore(); }
    @Profile("local")
    @Bean MembershipStore membershipStoreLocal() { return new InMemoryMembershipStore(); }

    // --- Dynamo (dev/prod) ---
    @Profile({"dev","prod"})
    @Bean DynamoDbClient dynamoClient() { return DynamoConfig.defaultClient(); }

    @Profile({"dev","prod"})
    @Bean UserStore userStoreDynamo(DynamoDbClient db,
                                    @Value("${antfarm.tables.users:antfarm_users}") String table) {
        return new DynamoUserStore(db, table);
    }

    @Profile({"dev","prod"})
    @Bean RoomStore roomStoreDynamo(DynamoDbClient db,
                                    @Value("${antfarm.tables.rooms:antfarm_rooms}") String table) {
        return new DynamoRoomStore(db, table);
    }

    @Profile({"dev","prod"})
    @Bean MessageStore messageStoreDynamo(DynamoDbClient db,
                                          @Value("${antfarm.tables.messages:antfarm_messages}") String table) {
        return new DynamoMessageStore(db, table);
    }

    @Profile({"dev","prod"})
    @Bean MembershipStore membershipStoreDynamo(DynamoDbClient db,
                                                @Value("${antfarm.tables.memberships:antfarm_memberships}") String table) {
        return new DynamoMembershipStore(db, table);
    }
}
