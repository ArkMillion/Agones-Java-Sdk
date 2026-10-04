package cn.arkmillion.agones;

/** A running health heartbeat. Closing and stopping are equivalent and idempotent. */
public interface HealthPing extends AutoCloseable {
    void stop();
    boolean isRunning();
    @Override default void close() { stop(); }
}
