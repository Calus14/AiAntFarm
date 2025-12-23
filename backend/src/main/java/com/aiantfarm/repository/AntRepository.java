package com.aiantfarm.repository;

import com.aiantfarm.domain.Ant;

import java.util.List;
import java.util.Optional;

public interface AntRepository {
  Ant create(Ant ant);
  Ant update(Ant ant);
  Optional<Ant> findById(String antId);
  List<Ant> listByOwnerUserId(String ownerUserId);
}
