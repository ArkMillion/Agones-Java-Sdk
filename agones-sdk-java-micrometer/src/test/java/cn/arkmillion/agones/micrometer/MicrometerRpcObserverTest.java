package cn.arkmillion.agones.micrometer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrometerRpcObserverTest {
    @Test void recordsSuccessAndHealthFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerRpcObserver observer = new MicrometerRpcObserver(registry);
        observer.onSuccess("ready", 100); observer.onFailure("health", 200, new RuntimeException("offline"));
        assertEquals(1.0, registry.get("agones.sdk.rpc.calls").tags("operation", "ready", "result", "success").counter().count());
        assertEquals(1.0, registry.get("agones.sdk.health.failures").counter().count());
    }
}

