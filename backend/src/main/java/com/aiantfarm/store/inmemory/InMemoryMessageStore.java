package com.aiantfarm.store.inmemory;

import com.aiantfarm.domain.Message;
import com.aiantfarm.store.MessageStore;
import com.aiantfarm.store.Page;

import java.util.*;

public class InMemoryMessageStore extends InMemoryBase<Message> implements MessageStore {
    @Override public Message create(Message m) { bucket(m.tenantId()).put(m.id(), m); return m; }
    @Override public Optional<Message> findById(String tenantId, String messageId) {
        return Optional.ofNullable(bucket(tenantId).get(messageId));
    }
    @Override public Page<Message> listByRoom(String tenantId, String roomId, int limit, String nextToken) {
        List<Message> all = new ArrayList<>(bucket(tenantId).values());
        all.removeIf(m -> !m.roomId().equals(roomId));
        all.sort(Comparator.comparing(Message::createdAt));
        int start = indexFromToken(nextToken);
        int end = Math.min(start + Math.max(limit, 1), all.size());
        return Page.of(all.subList(start, end), end < all.size() ? tokenFromIndex(end) : null);
    }
    @Override public boolean delete(String tenantId, String messageId) {
        return bucket(tenantId).remove(messageId) != null;
    }
}
