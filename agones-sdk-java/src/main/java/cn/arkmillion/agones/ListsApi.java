package cn.arkmillion.agones;

import com.google.protobuf.FieldMask;
import cn.arkmillion.agones.internal.RpcCalls;
import cn.arkmillion.agones.internal.proto.beta.AddListValueRequest;
import cn.arkmillion.agones.internal.proto.beta.GetListRequest;
import cn.arkmillion.agones.internal.proto.beta.RemoveListValueRequest;
import cn.arkmillion.agones.internal.proto.beta.SDKGrpc;
import cn.arkmillion.agones.internal.proto.beta.UpdateListRequest;
import cn.arkmillion.agones.model.GameServerList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Thread-safe facade for Agones beta List operations. */
public final class ListsApi {
    private final Supplier<SDKGrpc.SDKBlockingStub> stub;
    private final RpcCalls calls;
    ListsApi(Supplier<SDKGrpc.SDKBlockingStub> stub, RpcCalls calls) { this.stub = stub; this.calls = calls; }

    public void append(String name, String value) { requireValue(value); calls.run("lists.append", () -> appendDirect(name, value)); }
    public CompletableFuture<Void> appendAsync(String name, String value) { requireValue(value); return calls.asyncRun("lists.append", () -> appendDirect(name, value)); }
    public void delete(String name, String value) { requireValue(value); calls.run("lists.delete", () -> deleteDirect(name, value)); }
    public CompletableFuture<Void> deleteAsync(String name, String value) { requireValue(value); return calls.asyncRun("lists.delete", () -> deleteDirect(name, value)); }
    public void setCapacity(String name, long capacity) { CountersApi.requireNonNegative(capacity, "capacity"); update(name, capacity, null, "capacity"); }
    public CompletableFuture<Void> setCapacityAsync(String name, long capacity) { CountersApi.requireNonNegative(capacity, "capacity"); return updateAsync("lists.setCapacity", name, capacity, null, "capacity"); }
    public GameServerList get(String name) { CountersApi.requireName(name); return calls.call("lists.get", () -> fromProto(getDirect(name))); }
    public CompletableFuture<GameServerList> getAsync(String name) { CountersApi.requireName(name); return calls.async("lists.get", () -> fromProto(getDirect(name))); }
    public long getCapacity(String name) { return get(name).getCapacity(); }
    public CompletableFuture<Long> getCapacityAsync(String name) { return calls.async("lists.getCapacity", () -> getDirect(name).getCapacity()); }
    public boolean contains(String name, String value) { requireValue(value); return get(name).contains(value); }
    public CompletableFuture<Boolean> containsAsync(String name, String value) { requireValue(value); return calls.async("lists.contains", () -> getDirect(name).getValuesList().contains(value)); }
    public int getLength(String name) { return get(name).size(); }
    public CompletableFuture<Integer> getLengthAsync(String name) { return calls.async("lists.getLength", () -> getDirect(name).getValuesCount()); }
    public List<String> getValues(String name) { return get(name).getValues(); }
    public CompletableFuture<List<String>> getValuesAsync(String name) { return calls.async("lists.getValues", () -> immutable(getDirect(name).getValuesList())); }
    public void setValues(String name, List<String> values) { requireValues(values); update(name, 0, values, "values"); }
    public CompletableFuture<Void> setValuesAsync(String name, List<String> values) { requireValues(values); final List<String> copy = immutable(values); return updateAsync("lists.setValues", name, 0, copy, "values"); }

    private cn.arkmillion.agones.internal.proto.beta.List getDirect(String name) { CountersApi.requireName(name); return stub.get().getList(GetListRequest.newBuilder().setName(name).build()); }
    private Object appendDirect(String name, String value) { CountersApi.requireName(name); return stub.get().addListValue(AddListValueRequest.newBuilder().setName(name).setValue(value).build()); }
    private Object deleteDirect(String name, String value) { CountersApi.requireName(name); return stub.get().removeListValue(RemoveListValueRequest.newBuilder().setName(name).setValue(value).build()); }
    private void update(String name, long capacity, List<String> values, String path) { CountersApi.requireName(name); calls.run("lists.update", () -> updateDirect(name, capacity, values, path)); }
    private Object updateDirect(String name, long capacity, List<String> values, String path) {
        cn.arkmillion.agones.internal.proto.beta.List.Builder list = cn.arkmillion.agones.internal.proto.beta.List.newBuilder().setName(name).setCapacity(capacity);
        if (values != null) list.addAllValues(values);
        return stub.get().updateList(UpdateListRequest.newBuilder().setList(list).setUpdateMask(FieldMask.newBuilder().addPaths(path)).build());
    }
    private CompletableFuture<Void> updateAsync(String operation, String name, long capacity, List<String> values, String path) {
        CountersApi.requireName(name); return calls.asyncRun(operation, () -> updateDirect(name, capacity, values, path));
    }
    private static GameServerList fromProto(cn.arkmillion.agones.internal.proto.beta.List value) {
        return new GameServerList(value.getName(), value.getCapacity(), value.getValuesList());
    }
    private static void requireValue(String value) { if (value == null || value.isEmpty()) throw new AgonesSdkException("value must not be empty"); }
    private static void requireValues(List<String> values) { if (values == null) throw new AgonesSdkException("values must not be null"); for (String value : values) requireValue(value); }
    private static <T> List<T> immutable(List<T> values) { return Collections.unmodifiableList(new ArrayList<T>(values)); }
}
