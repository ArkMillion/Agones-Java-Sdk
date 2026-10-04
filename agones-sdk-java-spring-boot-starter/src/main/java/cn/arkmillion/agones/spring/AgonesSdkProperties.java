package cn.arkmillion.agones.spring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for the Agones sidecar connection and lifecycle. */
@ConfigurationProperties("agones.sdk")
public class AgonesSdkProperties {
    private boolean enabled = true;
    private String host = environment("AGONES_SDK_GRPC_HOST", "localhost");
    private int port = environmentPort();
    private Duration deadline = Duration.ofSeconds(10);
    private boolean tls;
    private boolean healthEnabled = true;
    private Duration healthPeriod = Duration.ofSeconds(2);
    private boolean shutdownOnExit = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public Duration getDeadline() { return deadline; }
    public void setDeadline(Duration deadline) { this.deadline = deadline; }
    public boolean isTls() { return tls; }
    public void setTls(boolean tls) { this.tls = tls; }
    public boolean isHealthEnabled() { return healthEnabled; }
    public void setHealthEnabled(boolean healthEnabled) { this.healthEnabled = healthEnabled; }
    public Duration getHealthPeriod() { return healthPeriod; }
    public void setHealthPeriod(Duration healthPeriod) { this.healthPeriod = healthPeriod; }
    public boolean isShutdownOnExit() { return shutdownOnExit; }
    public void setShutdownOnExit(boolean shutdownOnExit) { this.shutdownOnExit = shutdownOnExit; }

    private static String environment(String name, String fallback) { String value = System.getenv(name); return value == null || value.trim().isEmpty() ? fallback : value; }
    private static int environmentPort() { String value = System.getenv("AGONES_SDK_GRPC_PORT"); if (value == null) return 9357; try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return 9357; } }
}

