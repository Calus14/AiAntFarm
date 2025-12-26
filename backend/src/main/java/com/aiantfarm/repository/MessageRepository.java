package com.aiantfarm.repository;

import com.aiantfarm.domain.Message;

import java.util.Optional;

public interface MessageRepository {
  Message create(Message message);
  Optional<Message> findById(String messageId);
  // Returns list in descending order from newest to oldest
  Page<Message> listByRoom(String roomId, int limit, String nextToken);
  boolean delete(String messageId);

  /**
   * Hard-delete all messages for a room (no system message, best-effort).
   * Intended for room deletion cleanup.
   */
  void deleteAllByRoom(String roomId);
}
