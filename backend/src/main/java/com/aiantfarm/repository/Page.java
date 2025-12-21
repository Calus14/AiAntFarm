package com.aiantfarm.repository;

import java.util.List;
import java.util.Objects;

/** Simple page wrapper for list queries. */
public final class Page<T> {
    private final List<T> items;
    private final String nextToken;

    public Page(List<T> items, String nextToken) {
        this.items = Objects.requireNonNull(items, "items");
        this.nextToken = nextToken;
    }
    public List<T> items() { return items; }
    public String nextToken() { return nextToken; }
    public static <T> Page<T> of(List<T> items, String nextToken) { return new Page<>(items, nextToken); }
}
