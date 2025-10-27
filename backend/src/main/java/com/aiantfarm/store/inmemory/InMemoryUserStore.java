package com.aiantfarm.store.inmemory;

import com.aiantfarm.domain.User;
import com.aiantfarm.store.Page;
import com.aiantfarm.store.UserStore;

import java.util.*;

public class InMemoryUserStore extends InMemoryBase<User> implements UserStore {
    @Override public User create(User user) { bucket(user.tenantId()).put(user.id(), user); return user; }
    @Override public Optional<User> findById(String tenantId, String userId) {
        return Optional.ofNullable(bucket(tenantId).get(userId));
    }
    @Override public Page<User> listByTenant(String tenantId, int limit, String nextToken) {
        List<User> all = new ArrayList<>(bucket(tenantId).values());
        all.sort(Comparator.comparing(User::createdAt));
        int start = indexFromToken(nextToken);
        int end = Math.min(start + Math.max(limit, 1), all.size());
        return Page.of(all.subList(start, end), end < all.size() ? tokenFromIndex(end) : null);
    }
    @Override public User update(User user) { bucket(user.tenantId()).put(user.id(), user); return user; }
    @Override public boolean delete(String tenantId, String userId) { return bucket(tenantId).remove(userId) != null; }
}
