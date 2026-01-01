package com.aiantfarm.repository;

import com.aiantfarm.repository.entity.PasswordResetTokenEntity;
import java.util.Optional;

public interface PasswordResetTokenRepository {
  void save(PasswordResetTokenEntity entity);
  Optional<PasswordResetTokenEntity> findByTokenId(String tokenId);
  void markUsed(String tokenId);
}

