package com.aiantfarm.repository;

import com.aiantfarm.domain.Message;

import java.util.Optional;

public interface MessageRepository {
    Message create(Message message);
    Optional<Message> findById(String messageId);
    Page<Message> listByRoom(String roomId, int limit, String nextToken);
    boolean delete(String messageId);
}

