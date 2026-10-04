package cn.arkmillion.agones.micrometer;

import cn.arkmillion.agones.RpcObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Records SDK latency, success, and failure metrics without adding Micrometer to the core artifact. */
public final class MicrometerRpcObserver implements RpcObserver {
    private final MeterRegistry registry;
    public MicrometerRpcObserver(MeterRegistry registry) { this.registry = Objects.requireNonNull(registry, "registry"); }

    @Override public void onSuccess(String operation, long elapsedNanos) {
        timer(operation, "success").record(elapsedNanos, TimeUnit.NANOSECONDS);
        Counter.builder("agones.sdk.rpc.calls").tag("operation", operation).tag("result", "success").register(registry).increment();
    }
    @Override public void onFailure(String operation, long elapsedNanos, Throwable error) {
        timer(operation, "failure").record(elapsedNanos, TimeUnit.NANOSECONDS);
        Counter.builder("agones.sdk.rpc.calls").tag("operation", operation).tag("result", "failure").register(registry).increment();
        if ("health".equals(operation)) Counter.builder("agones.sdk.health.failures").register(registry).increment();
    }
    private Timer timer(String operation, String result) {
        return Timer.builder("agones.sdk.rpc.duration").description("Agones SDK RPC latency")
                .tag("operation", operation).tag("result", result).publishPercentileHistogram().register(registry);
    }
}

