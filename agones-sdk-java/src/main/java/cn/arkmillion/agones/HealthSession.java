package cn.arkmillion.agones;

import java.util.Optional;

/** A restartable, long-lived Agones health stream. */
public interface HealthSession extends AutoCloseable {
  void start();

  void stop();

  boolean isRunning();

  Optional<Throwable> getLastFailure();

  @Override
  default void close() {
    stop();
  }
}
