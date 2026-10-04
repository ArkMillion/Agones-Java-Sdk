package cn.arkmillion.agones.flow;

import cn.arkmillion.agones.AgonesSdk;
import cn.arkmillion.agones.model.GameServer;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** A demand-aware JDK Flow adapter for {@link AgonesSdk#watchGameServer}. */
public final class GameServerPublisher implements Flow.Publisher<GameServer> {
  private final AgonesSdk sdk;

  public GameServerPublisher(AgonesSdk sdk) {
    this.sdk = Objects.requireNonNull(sdk, "sdk");
  }

  @Override
  public void subscribe(Flow.Subscriber<? super GameServer> subscriber) {
    Objects.requireNonNull(subscriber, "subscriber");
    WatchSubscription subscription = new WatchSubscription(subscriber);
    subscriber.onSubscribe(subscription);
    subscription.start();
  }

  private final class WatchSubscription implements Flow.Subscription {
    private final Flow.Subscriber<? super GameServer> subscriber;
    private final AtomicLong demand = new AtomicLong();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile AutoCloseable watch;

    WatchSubscription(Flow.Subscriber<? super GameServer> subscriber) {
      this.subscriber = subscriber;
    }

    void start() {
      if (cancelled.get()) return;
      try {
        watch =
            sdk.watchGameServer(
                value -> {
                  if (takeDemand() && !cancelled.get()) subscriber.onNext(value);
                });
      } catch (Throwable error) {
        cancelled.set(true);
        subscriber.onError(error);
      }
    }

    @Override
    public void request(long count) {
      if (count <= 0) {
        cancel();
        subscriber.onError(new IllegalArgumentException("demand must be positive"));
        return;
      }
      demand.getAndUpdate(
          current ->
              current == Long.MAX_VALUE || count >= Long.MAX_VALUE - current
                  ? Long.MAX_VALUE
                  : current + count);
    }

    @Override
    public void cancel() {
      if (cancelled.compareAndSet(false, true)) {
        AutoCloseable handle = watch;
        if (handle != null)
          try {
            handle.close();
          } catch (Exception ignored) {
          }
      }
    }

    private boolean takeDemand() {
      for (; ; ) {
        long current = demand.get();
        if (current == 0) return false;
        if (current == Long.MAX_VALUE || demand.compareAndSet(current, current - 1)) return true;
      }
    }
  }
}
