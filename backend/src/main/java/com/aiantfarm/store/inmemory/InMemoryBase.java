package com.aiantfarm.store.inmemory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryBase<T> {
    // tenantId -> (id -> T)
    protected final Map<String, Map<String, T>> byTenant = new ConcurrentHashMap<>();

    protected Map<String, T> bucket(String tenantId) {
        return byTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }

    protected static String tokenFromIndex(int idx) { return idx <= 0 ? null : Integer.toString(idx); }
    protected static int indexFromToken(String token) {
        if (token == null || token.isBlank()) return 0;
        try { return Integer.parseInt(token); } catch (Exception e) { return 0; }
    }
}
