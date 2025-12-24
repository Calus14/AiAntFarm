package com.aiantfarm.repository;

import com.aiantfarm.domain.Ant;

import java.util.List;
import java.util.Optional;

public interface AntRepository {
  Ant create(Ant ant);
  Ant update(Ant ant);
  Optional<Ant> findById(String antId);

  /**
   * MVP-only: full table scan for Ant META items.
   *
   * WARNING: do not call this on hot paths at scale.
   */
  List<Ant> listAll();

  /**
   * MVP-only: full table scan for Ant META items.
   *
   * WARNING: do not call this on hot paths at scale.
   */
  List<Ant> listByOwnerUserId(String ownerUserId);
}
