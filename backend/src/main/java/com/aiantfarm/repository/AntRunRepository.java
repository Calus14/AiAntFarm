package com.aiantfarm.repository;

import com.aiantfarm.domain.AntRun;

import java.util.List;

public interface AntRunRepository {
  AntRun create(AntRun run);
  AntRun update(AntRun run);
  List<AntRun> listByAnt(String antId, int limit);
}

