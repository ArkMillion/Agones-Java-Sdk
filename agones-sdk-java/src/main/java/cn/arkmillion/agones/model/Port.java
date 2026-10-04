package cn.arkmillion.agones.model;

import java.util.Objects;

/** Named port exposed by a GameServer. */
public final class Port {
    private final String name;
    private final int port;
    public Port(String name, int port) { this.name = Objects.requireNonNull(name, "name"); this.port = port; }
    public String getName() { return name; }
    public int getPort() { return port; }
    @Override public boolean equals(Object other) { return this == other || other instanceof Port && port == ((Port) other).port && name.equals(((Port) other).name); }
    @Override public int hashCode() { return Objects.hash(name, port); }
    @Override public String toString() { return "Port{name='" + name + "', port=" + port + "}"; }
}

