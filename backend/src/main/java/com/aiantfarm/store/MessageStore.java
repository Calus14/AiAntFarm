package com.aiantfarm.store;

import com.aiantfarm.domain.Message;
import java.util.Optional;

public interface MessageStore {
    Message create(Message message);
    Optional<Message> findById(String tenantId, String messageId);
    Page<Message> listByRoom(String tenantId, String roomId, int limit, String nextToken);
    boolean delete(String tenantId, String messageId);
}
