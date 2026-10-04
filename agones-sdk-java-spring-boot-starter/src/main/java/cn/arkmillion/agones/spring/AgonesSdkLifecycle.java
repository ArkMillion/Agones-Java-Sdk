package cn.arkmillion.agones.spring;

import cn.arkmillion.agones.AgonesSdk;
import cn.arkmillion.agones.HealthSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/** Starts health reporting with the application and performs graceful Agones shutdown. */
public final class AgonesSdkLifecycle implements SmartLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(AgonesSdkLifecycle.class);
    private final AgonesSdk sdk;
    private final AgonesSdkProperties properties;
    private volatile boolean running;
    private volatile HealthSession healthSession;
    AgonesSdkLifecycle(AgonesSdk sdk, AgonesSdkProperties properties) { this.sdk = sdk; this.properties = properties; }

    @Override public synchronized void start() {
        if (running) return;
        if (properties.isHealthEnabled()) healthSession = sdk.startHealthSession(properties.getHealthPeriod());
        running = true;
    }
    @Override public synchronized void stop() {
        if (!running) return;
        HealthSession session = healthSession; if (session != null) session.stop();
        if (properties.isShutdownOnExit()) try { sdk.shutdown(); } catch (RuntimeException error) { LOG.warn("Agones graceful shutdown failed", error); }
        running = false;
    }
    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE; }
}

