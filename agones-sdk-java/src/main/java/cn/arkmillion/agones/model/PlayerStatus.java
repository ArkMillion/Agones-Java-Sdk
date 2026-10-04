package cn.arkmillion.agones.model;

import java.util.List;
import java.util.Objects;

/** Legacy player-tracking status retained for wire compatibility with Agones 1.61. */
public final class PlayerStatus {
  private final long count;
  private final long capacity;
  private final List<String> ids;

  public PlayerStatus(long count, long capacity, List<String> ids) {
    this.count = count;
    this.capacity = capacity;
    this.ids = ModelCollections.immutableList(ids);
  }

  public long getCount() {
    return count;
  }

  public long getCapacity() {
    return capacity;
  }

  public List<String> getIds() {
    return ids;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof PlayerStatus
            && count == ((PlayerStatus) other).count
            && capacity == ((PlayerStatus) other).capacity
            && ids.equals(((PlayerStatus) other).ids);
  }

  @Override
  public int hashCode() {
    return Objects.hash(count, capacity, ids);
  }

  @Override
  public String toString() {
    return "PlayerStatus{count=" + count + ", capacity=" + capacity + "}";
  }
}
