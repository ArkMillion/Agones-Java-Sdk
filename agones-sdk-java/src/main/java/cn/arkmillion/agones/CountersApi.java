package cn.arkmillion.agones;

import com.google.protobuf.Int64Value;
import cn.arkmillion.agones.internal.RpcCalls;
import cn.arkmillion.agones.internal.proto.beta.CounterUpdateRequest;
import cn.arkmillion.agones.internal.proto.beta.GetCounterRequest;
import cn.arkmillion.agones.internal.proto.beta.SDKGrpc;
import cn.arkmillion.agones.internal.proto.beta.UpdateCounterRequest;
import cn.arkmillion.agones.model.Counter;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Thread-safe facade for Agones beta Counter operations. */
public final class CountersApi {
    private final Supplier<SDKGrpc.SDKBlockingStub> stub;
    private final RpcCalls calls;
    CountersApi(Supplier<SDKGrpc.SDKBlockingStub> stub, RpcCalls calls) { this.stub = stub; this.calls = calls; }

    public Counter get(String name) { requireName(name); return calls.call("counters.get", () -> fromProto(getDirect(name))); }
    public CompletableFuture<Counter> getAsync(String name) { requireName(name); return calls.async("counters.get", () -> fromProto(getDirect(name))); }
    public long getCount(String name) { return get(name).getCount(); }
    public CompletableFuture<Long> getCountAsync(String name) { return calls.async("counters.getCount", () -> getDirect(name).getCount()); }
    public void setCount(String name, long value) { update(name, Int64Value.of(value), null, 0); }
    public CompletableFuture<Void> setCountAsync(String name, long value) { return updateAsync("counters.setCount", name, Int64Value.of(value), null, 0); }
    public void increment(String name, long amount) { requireNonNegative(amount, "amount"); update(name, null, null, amount); }
    public CompletableFuture<Void> incrementAsync(String name, long amount) { requireNonNegative(amount, "amount"); return updateAsync("counters.increment", name, null, null, amount); }
    public void decrement(String name, long amount) { requireNonNegative(amount, "amount"); update(name, null, null, -amount); }
    public CompletableFuture<Void> decrementAsync(String name, long amount) { requireNonNegative(amount, "amount"); return updateAsync("counters.decrement", name, null, null, -amount); }
    public long getCapacity(String name) { return get(name).getCapacity(); }
    public CompletableFuture<Long> getCapacityAsync(String name) { return calls.async("counters.getCapacity", () -> getDirect(name).getCapacity()); }
    public void setCapacity(String name, long value) { requireNonNegative(value, "capacity"); update(name, null, Int64Value.of(value), 0); }
    public CompletableFuture<Void> setCapacityAsync(String name, long value) { requireNonNegative(value, "capacity"); return updateAsync("counters.setCapacity", name, null, Int64Value.of(value), 0); }

    private cn.arkmillion.agones.internal.proto.beta.Counter getDirect(String name) { requireName(name); return stub.get().getCounter(GetCounterRequest.newBuilder().setName(name).build()); }
    private void update(String name, Int64Value count, Int64Value capacity, long diff) {
        requireName(name); calls.run("counters.update", () -> updateDirect(name, count, capacity, diff));
    }
    private cn.arkmillion.agones.internal.proto.beta.Counter updateDirect(String name, Int64Value count, Int64Value capacity, long diff) {
        CounterUpdateRequest.Builder update = CounterUpdateRequest.newBuilder().setName(name).setCountDiff(diff);
        if (count != null) update.setCount(count);
        if (capacity != null) update.setCapacity(capacity);
        return stub.get().updateCounter(UpdateCounterRequest.newBuilder().setCounterUpdateRequest(update).build());
    }
    private static Counter fromProto(cn.arkmillion.agones.internal.proto.beta.Counter value) {
        return new Counter(value.getName(), value.getCount(), value.getCapacity());
    }
    private CompletableFuture<Void> updateAsync(String operation, String name, Int64Value count, Int64Value capacity, long diff) {
        requireName(name); return calls.asyncRun(operation, () -> updateDirect(name, count, capacity, diff));
    }
    static void requireName(String name) { if (name == null || name.trim().isEmpty()) throw new AgonesSdkException("name must not be blank"); }
    static void requireNonNegative(long value, String label) { if (value < 0) throw new AgonesSdkException(label + " must be non-negative"); }
}

