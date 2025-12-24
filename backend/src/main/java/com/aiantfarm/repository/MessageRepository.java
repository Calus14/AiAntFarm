package com.aiantfarm.repository;

import com.aiantfarm.domain.Message;

import java.util.Optional;

public interface MessageRepository {
  Message create(Message message);
  Optional<Message> findById(String messageId);
  // Returns list in descending order from newest to oldest
  Page<Message> listByRoom(String roomId, int limit, String nextToken);
  boolean delete(String messageId);
}

