package com.aiantfarm.config;

import com.aiantfarm.domain.Message;
import com.aiantfarm.domain.Room;
import com.aiantfarm.domain.User;
import com.aiantfarm.store.MessageStore;
import com.aiantfarm.store.RoomStore;
import com.aiantfarm.store.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;

import java.util.Optional;

/**
 * Seeds local data:
 * - default admin User
 * - 'General' Room
 * - hello Message in 'General'
 */
@Component
@Profile("local")
public class SeedDataLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final UserStore userStore;
    private final RoomStore roomStore;
    private final MessageStore messageStore;

    @Value("${antfarm.seed.tenant:tenant-local}")
    private String tenantId;

    @Value("${antfarm.seed.adminUsername:admin}")
    private String adminUsername;

    @Value("${antfarm.seed.adminDisplayName:Admin}")
    private String adminDisplayName;

    @Value("${antfarm.seed.roomName:General}")
    private String generalRoomName;

    @Value("${antfarm.seed.helloText:Hello from SeedDataLoader!}")
    private String helloText;

    public SeedDataLoader(UserStore userStore, RoomStore roomStore, MessageStore messageStore) {
        this.userStore = userStore;
        this.roomStore = roomStore;
        this.messageStore = messageStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 1) Ensure admin user
            User admin = ensureAdminUser();

            // 2) Ensure 'General' room
            Room general = ensureGeneralRoom(admin);

            // 3) Ensure a hello message exists (only if room had no messages yet)
            seedHelloMessageIfEmpty(general, admin);

            log.info("Local seed complete. tenantId='{}', admin='{}', room='{}'", tenantId, adminUsername, generalRoomName);
        } catch (Exception e) {
            log.warn("SeedDataLoader failed (non-fatal): {}", e.getMessage(), e);
        }
    }

    private User ensureAdminUser() {
        // Try to locate by listing all users in tenant and matching username (MVP approach).
        // If you add a UserStore.findByUsername later, switch to that.
        var page = userStore.listByTenant(tenantId, 1000, null);
        Optional<User> existing = page.items().stream()
                .filter(u -> adminUsername.equalsIgnoreCase(u.username()))
                .findFirst();

        if (existing.isPresent()) {
            return existing.get();
        }
        User created = User.create(tenantId, adminUsername, adminDisplayName);
        userStore.create(created);
        return created;
    }

    private Room ensureGeneralRoom(User admin) {
        Optional<Room> byName = roomStore.findByName(tenantId, generalRoomName);
        if (byName.isPresent()) {
            return byName.get();
        }
        Room created = Room.create(tenantId, generalRoomName, admin.id());
        roomStore.create(created);
        return created;
    }

    private void seedHelloMessageIfEmpty(Room general, User admin) {
        var page = messageStore.listByRoom(tenantId, general.id(), 1, null);
        if (!page.items().isEmpty()) {
            return; // room already has messages
        }
        Message hello = Message.createUser(tenantId, general.id(), admin.id(), helloText);
        messageStore.create(hello);
    }
}
