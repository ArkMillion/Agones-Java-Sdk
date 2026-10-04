package cn.arkmillion.agones.model;

import java.util.List;
import java.util.Objects;

/** Immutable Agones List value. */
public final class GameServerList {
    private final String name;
    private final long capacity;
    private final List<String> values;
    public GameServerList(String name, long capacity, List<String> values) { this.name = Objects.requireNonNull(name, "name"); this.capacity = capacity; this.values = ModelCollections.immutableList(values); }
    public String getName() { return name; }
    public long getCapacity() { return capacity; }
    public List<String> getValues() { return values; }
    public int size() { return values.size(); }
    public boolean contains(String value) { return values.contains(value); }
    @Override public boolean equals(Object other) { return this == other || other instanceof GameServerList && capacity == ((GameServerList) other).capacity && name.equals(((GameServerList) other).name) && values.equals(((GameServerList) other).values); }
    @Override public int hashCode() { return Objects.hash(name, capacity, values); }
    @Override public String toString() { return "GameServerList{name='" + name + "', capacity=" + capacity + ", size=" + values.size() + "}"; }
}

