package cn.arkmillion.agones.model;

import java.util.Objects;

/** Immutable Agones health-check configuration. */
public final class HealthConfiguration {
    private final boolean disabled;
    private final int periodSeconds;
    private final int failureThreshold;
    private final int initialDelaySeconds;
    public HealthConfiguration(boolean disabled, int periodSeconds, int failureThreshold, int initialDelaySeconds) { this.disabled = disabled; this.periodSeconds = periodSeconds; this.failureThreshold = failureThreshold; this.initialDelaySeconds = initialDelaySeconds; }
    public boolean isDisabled() { return disabled; }
    public int getPeriodSeconds() { return periodSeconds; }
    public int getFailureThreshold() { return failureThreshold; }
    public int getInitialDelaySeconds() { return initialDelaySeconds; }
    @Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof HealthConfiguration)) return false; HealthConfiguration that = (HealthConfiguration) other; return disabled == that.disabled && periodSeconds == that.periodSeconds && failureThreshold == that.failureThreshold && initialDelaySeconds == that.initialDelaySeconds; }
    @Override public int hashCode() { return Objects.hash(disabled, periodSeconds, failureThreshold, initialDelaySeconds); }
    @Override public String toString() { return "HealthConfiguration{disabled=" + disabled + ", periodSeconds=" + periodSeconds + ", failureThreshold=" + failureThreshold + "}"; }
}

