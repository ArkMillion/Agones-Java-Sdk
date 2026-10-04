package cn.arkmillion.agones.spring;

import cn.arkmillion.agones.AgonesSdk;
import cn.arkmillion.agones.HealthPing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/** Starts health reporting with the application and performs graceful Agones shutdown. */
public final class AgonesSdkLifecycle implements SmartLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(AgonesSdkLifecycle.class);
    private final AgonesSdk sdk;
    private final AgonesSdkProperties properties;
    private volatile boolean running;
    private volatile HealthPing healthPing;
    AgonesSdkLifecycle(AgonesSdk sdk, AgonesSdkProperties properties) { this.sdk = sdk; this.properties = properties; }

    @Override public synchronized void start() {
        if (running) return;
        if (properties.isHealthEnabled()) healthPing = sdk.startHealthPing(properties.getHealthPeriod());
        running = true;
    }
    @Override public synchronized void stop() {
        if (!running) return;
        HealthPing ping = healthPing; if (ping != null) ping.stop();
        if (properties.isShutdownOnExit()) try { sdk.shutdown(); } catch (RuntimeException error) { LOG.warn("Agones graceful shutdown failed", error); }
        running = false;
    }
    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE; }
}

