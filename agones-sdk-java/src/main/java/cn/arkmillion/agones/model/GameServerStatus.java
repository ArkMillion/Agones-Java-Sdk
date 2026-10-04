package cn.arkmillion.agones.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable runtime status exposed by the Agones SDK. */
public final class GameServerStatus {
    private final String state;
    private final String address;
    private final List<Address> addresses;
    private final List<Port> ports;
    private final PlayerStatus players;
    private final Map<String, Counter> counters;
    private final Map<String, GameServerList> lists;
    public GameServerStatus(String state, String address, List<Address> addresses, List<Port> ports, PlayerStatus players, Map<String, Counter> counters, Map<String, GameServerList> lists) { this.state = Objects.requireNonNull(state, "state"); this.address = Objects.requireNonNull(address, "address"); this.addresses = ModelCollections.immutableList(addresses); this.ports = ModelCollections.immutableList(ports); this.players = Objects.requireNonNull(players, "players"); this.counters = ModelCollections.immutableMap(counters); this.lists = ModelCollections.immutableMap(lists); }
    public String getState() { return state; }
    public String getAddress() { return address; }
    public List<Address> getAddresses() { return addresses; }
    public List<Port> getPorts() { return ports; }
    public PlayerStatus getPlayers() { return players; }
    public Map<String, Counter> getCounters() { return counters; }
    public Map<String, GameServerList> getLists() { return lists; }
    @Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof GameServerStatus)) return false; GameServerStatus that = (GameServerStatus) other; return state.equals(that.state) && address.equals(that.address) && addresses.equals(that.addresses) && ports.equals(that.ports) && players.equals(that.players) && counters.equals(that.counters) && lists.equals(that.lists); }
    @Override public int hashCode() { return Objects.hash(state, address, addresses, ports, players, counters, lists); }
    @Override public String toString() { return "GameServerStatus{state='" + state + "', address='" + address + "', ports=" + ports.size() + "}"; }
}
