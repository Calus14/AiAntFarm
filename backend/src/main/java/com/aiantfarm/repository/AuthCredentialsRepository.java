package com.aiantfarm.repository;

import com.aiantfarm.repository.entity.AuthCredentialsEntity;

import java.util.Optional;

public interface AuthCredentialsRepository {
  AuthCredentialsEntity create(AuthCredentialsEntity entity);

  Optional<AuthCredentialsEntity> findByUserId(String userId);
  Optional<AuthCredentialsEntity> findByEmail(String email);

  boolean deleteByUserId(String userId);
}

