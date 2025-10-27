package com.aiantfarm.store;

import com.aiantfarm.domain.User;
import java.util.Optional;

public interface UserStore {
    User create(User user);
    Optional<User> findById(String tenantId, String userId);
    Page<User> listByTenant(String tenantId, int limit, String nextToken);
    User update(User user);
    boolean delete(String tenantId, String userId);
}
