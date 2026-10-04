package cn.arkmillion.agones.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable public representation of an Agones GameServer. */
public final class GameServer {
    private final ObjectMeta objectMeta;
    private final Spec spec;
    private final Status status;

    public GameServer(ObjectMeta objectMeta, Spec spec, Status status) {
        this.objectMeta = Objects.requireNonNull(objectMeta, "objectMeta");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.status = Objects.requireNonNull(status, "status");
    }
    public ObjectMeta getObjectMeta() { return objectMeta; }
    public Spec getSpec() { return spec; }
    public Status getStatus() { return status; }

    public static final class ObjectMeta {
        private final String name, namespace, uid, resourceVersion;
        private final long generation;
        private final Instant creationTimestamp, deletionTimestamp;
        private final Map<String, String> annotations, labels;
        public ObjectMeta(String name, String namespace, String uid, String resourceVersion, long generation,
                          Instant creationTimestamp, Instant deletionTimestamp,
                          Map<String, String> annotations, Map<String, String> labels) {
            this.name = name; this.namespace = namespace; this.uid = uid; this.resourceVersion = resourceVersion;
            this.generation = generation; this.creationTimestamp = creationTimestamp; this.deletionTimestamp = deletionTimestamp;
            this.annotations = immutableMap(annotations); this.labels = immutableMap(labels);
        }
        public String getName() { return name; }
        public String getNamespace() { return namespace; }
        public String getUid() { return uid; }
        public String getResourceVersion() { return resourceVersion; }
        public long getGeneration() { return generation; }
        public Instant getCreationTimestamp() { return creationTimestamp; }
        public Instant getDeletionTimestamp() { return deletionTimestamp; }
        public Map<String, String> getAnnotations() { return annotations; }
        public Map<String, String> getLabels() { return labels; }
    }

    public static final class Spec {
        private final Health health;
        public Spec(Health health) { this.health = Objects.requireNonNull(health, "health"); }
        public Health getHealth() { return health; }
    }

    public static final class Health {
        private final boolean disabled;
        private final int periodSeconds, failureThreshold, initialDelaySeconds;
        public Health(boolean disabled, int periodSeconds, int failureThreshold, int initialDelaySeconds) {
            this.disabled = disabled; this.periodSeconds = periodSeconds; this.failureThreshold = failureThreshold;
            this.initialDelaySeconds = initialDelaySeconds;
        }
        public boolean isDisabled() { return disabled; }
        public int getPeriodSeconds() { return periodSeconds; }
        public int getFailureThreshold() { return failureThreshold; }
        public int getInitialDelaySeconds() { return initialDelaySeconds; }
    }

    public static final class Status {
        private final String state, address;
        private final List<Address> addresses;
        private final List<Port> ports;
        private final PlayerStatus players;
        private final Map<String, CounterStatus> counters;
        private final Map<String, ListStatus> lists;
        public Status(String state, String address, List<Address> addresses, List<Port> ports, PlayerStatus players,
                      Map<String, CounterStatus> counters, Map<String, ListStatus> lists) {
            this.state = state; this.address = address; this.addresses = immutableList(addresses);
            this.ports = immutableList(ports); this.players = players;
            this.counters = immutableMap(counters); this.lists = immutableMap(lists);
        }
        public String getState() { return state; }
        public String getAddress() { return address; }
        public List<Address> getAddresses() { return addresses; }
        public List<Port> getPorts() { return ports; }
        public PlayerStatus getPlayers() { return players; }
        public Map<String, CounterStatus> getCounters() { return counters; }
        public Map<String, ListStatus> getLists() { return lists; }
    }

    public static final class Address {
        private final String type, address;
        public Address(String type, String address) { this.type = type; this.address = address; }
        public String getType() { return type; }
        public String getAddress() { return address; }
    }
    public static final class Port {
        private final String name; private final int port;
        public Port(String name, int port) { this.name = name; this.port = port; }
        public String getName() { return name; }
        public int getPort() { return port; }
    }
    public static final class PlayerStatus {
        private final long count, capacity; private final List<String> ids;
        public PlayerStatus(long count, long capacity, List<String> ids) {
            this.count = count; this.capacity = capacity; this.ids = immutableList(ids);
        }
        public long getCount() { return count; }
        public long getCapacity() { return capacity; }
        public List<String> getIds() { return ids; }
    }
    public static final class CounterStatus {
        private final long count, capacity;
        public CounterStatus(long count, long capacity) { this.count = count; this.capacity = capacity; }
        public long getCount() { return count; }
        public long getCapacity() { return capacity; }
    }
    public static final class ListStatus {
        private final long capacity; private final List<String> values;
        public ListStatus(long capacity, List<String> values) { this.capacity = capacity; this.values = immutableList(values); }
        public long getCapacity() { return capacity; }
        public List<String> getValues() { return values; }
    }

    private static <T> List<T> immutableList(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }
    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<K, V>(source));
    }
}

