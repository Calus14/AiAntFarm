package com.aiantfarm.repository;

import com.aiantfarm.domain.User;

import java.util.Optional;

public interface UserRepository {
  User create(User user);
  User update(User user);
  Optional<User> findByUserId(String userId);
  boolean deleteByUserId(String userId);
}

