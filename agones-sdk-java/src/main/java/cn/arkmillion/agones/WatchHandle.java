package cn.arkmillion.agones;

import java.util.Optional;

/** Cancellable handle for an automatically reconnecting GameServer watch. */
public interface WatchHandle extends AutoCloseable {
  WatchState getState();

  Optional<Throwable> getLastFailure();

  boolean isClosed();

  @Override
  void close();
}
