package cn.arkmillion.agones.model;

import java.util.Objects;

/** Immutable Agones Counter value. */
public final class Counter {
    private final String name;
    private final long count;
    private final long capacity;
    public Counter(String name, long count, long capacity) { this.name = Objects.requireNonNull(name, "name"); this.count = count; this.capacity = capacity; }
    public String getName() { return name; }
    public long getCount() { return count; }
    public long getCapacity() { return capacity; }
    @Override public boolean equals(Object other) { return this == other || other instanceof Counter && count == ((Counter) other).count && capacity == ((Counter) other).capacity && name.equals(((Counter) other).name); }
    @Override public int hashCode() { return Objects.hash(name, count, capacity); }
    @Override public String toString() { return "Counter{name='" + name + "', count=" + count + ", capacity=" + capacity + "}"; }
}

