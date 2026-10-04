package cn.arkmillion.agones.model;

import java.util.Objects;

/** Network address advertised by a GameServer. */
public final class Address {
    private final String type;
    private final String address;
    public Address(String type, String address) { this.type = Objects.requireNonNull(type, "type"); this.address = Objects.requireNonNull(address, "address"); }
    public String getType() { return type; }
    public String getAddress() { return address; }
    @Override public boolean equals(Object other) { return this == other || other instanceof Address && type.equals(((Address) other).type) && address.equals(((Address) other).address); }
    @Override public int hashCode() { return Objects.hash(type, address); }
    @Override public String toString() { return "Address{type='" + type + "', address='" + address + "'}"; }
}

