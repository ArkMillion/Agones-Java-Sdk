package cn.arkmillion.agones;

import cn.arkmillion.agones.internal.proto.Empty;
import cn.arkmillion.agones.internal.proto.GameServer;
import cn.arkmillion.agones.internal.proto.KeyValue;
import cn.arkmillion.agones.internal.proto.SDKGrpc;
import cn.arkmillion.agones.internal.proto.beta.AddListValueRequest;
import cn.arkmillion.agones.internal.proto.beta.Counter;
import cn.arkmillion.agones.internal.proto.beta.GetCounterRequest;
import cn.arkmillion.agones.internal.proto.beta.GetListRequest;
import cn.arkmillion.agones.internal.proto.beta.RemoveListValueRequest;
import cn.arkmillion.agones.internal.proto.beta.UpdateCounterRequest;
import cn.arkmillion.agones.internal.proto.beta.UpdateListRequest;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgonesSdkTest {
    private final FakeCore core = new FakeCore();
    private final FakeBeta beta = new FakeBeta();
    private Server server;
    private ManagedChannel channel;
    private AgonesSdk sdk;

    @BeforeEach void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(core).addService(beta).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        sdk = AgonesSdk.builder().channel(channel).deadline(Duration.ofSeconds(2)).build();
    }

    @AfterEach void tearDown() throws Exception {
        sdk.close(); channel.shutdownNow(); server.shutdownNow(); server.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test void lifecycleAndMetadataSupportBlockingAndAsyncCalls() throws Exception {
        sdk.ready(); sdk.allocateAsync().get(2, TimeUnit.SECONDS); sdk.reserve(Duration.ofSeconds(30)); sdk.shutdown();
        sdk.setLabel("map", "arena"); sdk.setAnnotationAsync("build", "42").get(2, TimeUnit.SECONDS);
        assertEquals(1, core.ready.get()); assertEquals(1, core.allocate.get()); assertEquals(30, core.reserveSeconds.get());
        assertEquals("map=arena", core.label.get()); assertEquals("build=42", core.annotation.get());
    }

    @Test void gameServerIsMappedToImmutablePublicModel() {
        cn.arkmillion.agones.model.GameServer gs = sdk.getGameServer();
        assertEquals("server-1", gs.getObjectMeta().getName());
        assertEquals("Ready", gs.getStatus().getState());
        assertEquals(2, gs.getStatus().getCounters().get("rooms").getCount());
        assertThrows(UnsupportedOperationException.class, () -> gs.getObjectMeta().getLabels().put("x", "y"));
    }

    @Test void countersExposeAllOperationsAndAsyncVariants() throws Exception {
        assertEquals(2, sdk.counters().getCount("rooms"));
        sdk.counters().increment("rooms", 2); sdk.counters().decrementAsync("rooms", 1).get(2, TimeUnit.SECONDS);
        sdk.counters().setCount("rooms", 7); sdk.counters().setCapacityAsync("rooms", 20).get(2, TimeUnit.SECONDS);
        assertEquals(7, sdk.counters().getCountAsync("rooms").get(2, TimeUnit.SECONDS));
        assertEquals(20, sdk.counters().getCapacity("rooms"));
        assertThrows(AgonesSdkException.class, () -> sdk.counters().increment("rooms", -1));
    }

    @Test void listsExposeAllOperationsAndImmutableValues() throws Exception {
        sdk.lists().append("players", "p2"); assertTrue(sdk.lists().contains("players", "p2"));
        sdk.lists().deleteAsync("players", "p2").get(2, TimeUnit.SECONDS);
        sdk.lists().setCapacity("players", 8); sdk.lists().setValuesAsync("players", Arrays.asList("a", "b")).get(2, TimeUnit.SECONDS);
        assertEquals(8, sdk.lists().getCapacityAsync("players").get(2, TimeUnit.SECONDS));
        assertEquals(2, sdk.lists().getLength("players"));
        assertThrows(UnsupportedOperationException.class, () -> sdk.lists().getValues("players").add("c"));
    }

    @Test void healthCompletesAndCloseIsIdempotent() {
        sdk.health(); assertEquals(1, core.health.get()); sdk.close(); sdk.close();
    }

    @Test void watchDeliversMappedUpdates() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AutoCloseable watch = sdk.watchGameServer(value -> { if ("Ready".equals(value.getStatus().getState())) received.countDown(); });
        assertTrue(received.await(2, TimeUnit.SECONDS));
        watch.close();
    }

    @Test void concurrentAsyncCallsAreThreadSafe() throws Exception {
        java.util.List<java.util.concurrent.CompletableFuture<Void>> calls = new java.util.ArrayList<java.util.concurrent.CompletableFuture<Void>>();
        for (int i = 0; i < 64; i++) calls.add(sdk.readyAsync());
        java.util.concurrent.CompletableFuture.allOf(calls.toArray(new java.util.concurrent.CompletableFuture<?>[0])).get(5, TimeUnit.SECONDS);
        assertEquals(64, core.ready.get());
    }

    private static final class FakeCore extends SDKGrpc.SDKImplBase {
        final AtomicInteger ready = new AtomicInteger(), allocate = new AtomicInteger(), health = new AtomicInteger();
        final AtomicLong reserveSeconds = new AtomicLong();
        final AtomicReference<String> label = new AtomicReference<String>(), annotation = new AtomicReference<String>();
        @Override public void ready(Empty request, StreamObserver<Empty> response) { ready.incrementAndGet(); ok(response); }
        @Override public void allocate(Empty request, StreamObserver<Empty> response) { allocate.incrementAndGet(); ok(response); }
        @Override public void shutdown(Empty request, StreamObserver<Empty> response) { ok(response); }
        @Override public void reserve(cn.arkmillion.agones.internal.proto.Duration request, StreamObserver<Empty> response) { reserveSeconds.set(request.getSeconds()); ok(response); }
        @Override public void setLabel(KeyValue request, StreamObserver<Empty> response) { label.set(request.getKey() + "=" + request.getValue()); ok(response); }
        @Override public void setAnnotation(KeyValue request, StreamObserver<Empty> response) { annotation.set(request.getKey() + "=" + request.getValue()); ok(response); }
        @Override public StreamObserver<Empty> health(StreamObserver<Empty> response) {
            return new StreamObserver<Empty>() {
                public void onNext(Empty value) { health.incrementAndGet(); }
                public void onError(Throwable t) { }
                public void onCompleted() { ok(response); }
            };
        }
        @Override public void getGameServer(Empty request, StreamObserver<GameServer> response) {
            response.onNext(gameServer()); response.onCompleted();
        }
        @Override public void watchGameServer(Empty request, StreamObserver<GameServer> response) { response.onNext(gameServer()); }
        private static GameServer gameServer() {
            return GameServer.newBuilder()
                    .setObjectMeta(GameServer.ObjectMeta.newBuilder().setName("server-1").putLabels("mode", "ranked"))
                    .setSpec(GameServer.Spec.newBuilder().setHealth(GameServer.Spec.Health.newBuilder().setPeriodSeconds(2)))
                    .setStatus(GameServer.Status.newBuilder().setState("Ready").setAddress("127.0.0.1")
                            .putCounters("rooms", GameServer.Status.CounterStatus.newBuilder().setCount(2).setCapacity(10).build())
                            .putLists("players", GameServer.Status.ListStatus.newBuilder().setCapacity(5).addValues("p1").build()))
                    .build();
        }
        private static void ok(StreamObserver<Empty> response) { response.onNext(Empty.getDefaultInstance()); response.onCompleted(); }
    }

    private static final class FakeBeta extends cn.arkmillion.agones.internal.proto.beta.SDKGrpc.SDKImplBase {
        long count = 2, capacity = 10, listCapacity = 5;
        java.util.List<String> values = new java.util.ArrayList<String>(Collections.singletonList("p1"));
        @Override public void getCounter(GetCounterRequest request, StreamObserver<Counter> response) { counter(response); }
        @Override public void updateCounter(UpdateCounterRequest request, StreamObserver<Counter> response) {
            cn.arkmillion.agones.internal.proto.beta.CounterUpdateRequest u = request.getCounterUpdateRequest();
            if (u.hasCount()) count = u.getCount().getValue();
            if (u.hasCapacity()) capacity = u.getCapacity().getValue();
            count += u.getCountDiff(); counter(response);
        }
        private void counter(StreamObserver<Counter> response) { response.onNext(Counter.newBuilder().setName("rooms").setCount(count).setCapacity(capacity).build()); response.onCompleted(); }
        @Override public void getList(GetListRequest request, StreamObserver<cn.arkmillion.agones.internal.proto.beta.List> response) { list(response); }
        @Override public void addListValue(AddListValueRequest request, StreamObserver<cn.arkmillion.agones.internal.proto.beta.List> response) { values.add(request.getValue()); list(response); }
        @Override public void removeListValue(RemoveListValueRequest request, StreamObserver<cn.arkmillion.agones.internal.proto.beta.List> response) { values.remove(request.getValue()); list(response); }
        @Override public void updateList(UpdateListRequest request, StreamObserver<cn.arkmillion.agones.internal.proto.beta.List> response) {
            if (request.getUpdateMask().getPathsList().contains("capacity")) listCapacity = request.getList().getCapacity();
            if (request.getUpdateMask().getPathsList().contains("values")) { values.clear(); values.addAll(request.getList().getValuesList()); }
            list(response);
        }
        private void list(StreamObserver<cn.arkmillion.agones.internal.proto.beta.List> response) {
            response.onNext(cn.arkmillion.agones.internal.proto.beta.List.newBuilder().setName("players").setCapacity(listCapacity).addAllValues(values).build()); response.onCompleted();
        }
    }
}
