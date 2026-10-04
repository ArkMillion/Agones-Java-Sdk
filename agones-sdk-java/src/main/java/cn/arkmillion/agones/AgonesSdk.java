package cn.arkmillion.agones;

import cn.arkmillion.agones.internal.GameServerMapper;
import cn.arkmillion.agones.internal.RpcCalls;
import cn.arkmillion.agones.internal.proto.Empty;
import cn.arkmillion.agones.internal.proto.KeyValue;
import cn.arkmillion.agones.internal.proto.SDKGrpc;
import cn.arkmillion.agones.model.GameServer;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thread-safe client for the Agones SDK sidecar. */
public final class AgonesSdk implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AgonesSdk.class);
    private static final Empty EMPTY = Empty.getDefaultInstance();
    private final ManagedChannel channel;
    private final boolean ownsChannel;
    private final long deadlineMillis;
    private final Executor asyncExecutor;
    private final boolean ownsExecutor;
    private final ScheduledExecutorService scheduler;
    private final RpcCalls calls;
    private final RpcObserver observer;
    private final CountersApi counters;
    private final ListsApi lists;
    private final List<AutoCloseable> backgroundTasks = new CopyOnWriteArrayList<AutoCloseable>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread shutdownHook;

    private AgonesSdk(Builder builder) {
        if (builder.channel == null) {
            NettyChannelBuilder channelBuilder = NettyChannelBuilder.forAddress(builder.host, builder.port);
            if (builder.tls) channelBuilder.useTransportSecurity(); else channelBuilder.usePlaintext();
            this.channel = channelBuilder.build();
            this.ownsChannel = true;
        } else {
            this.channel = builder.channel;
            this.ownsChannel = false;
        }
        this.deadlineMillis = builder.deadline.toMillis();
        if (builder.asyncExecutor == null) {
            this.asyncExecutor = Executors.newCachedThreadPool(daemonFactory("agones-async"));
            this.ownsExecutor = true;
        } else {
            this.asyncExecutor = builder.asyncExecutor;
            this.ownsExecutor = false;
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("agones-scheduler"));
        this.observer = builder.observer;
        this.calls = new RpcCalls(asyncExecutor, observer);
        this.counters = new CountersApi(() -> cn.arkmillion.agones.internal.proto.beta.SDKGrpc.newBlockingStub(channel)
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS), calls);
        this.lists = new ListsApi(() -> cn.arkmillion.agones.internal.proto.beta.SDKGrpc.newBlockingStub(channel)
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS), calls);
        if (builder.shutdownHook) {
            shutdownHook = new Thread(this::close, "agones-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } else shutdownHook = null;
    }

    public static AgonesSdk connect() { return builder().build(); }
    public static Builder builder() { return new Builder(); }
    public CountersApi counters() { ensureOpen(); return counters; }
    public ListsApi lists() { ensureOpen(); return lists; }

    public void ready() { unary("ready", () -> core().ready(EMPTY)); }
    public CompletableFuture<Void> readyAsync() { return unaryAsync("ready", () -> core().ready(EMPTY)); }
    public void allocate() { unary("allocate", () -> core().allocate(EMPTY)); }
    public CompletableFuture<Void> allocateAsync() { return unaryAsync("allocate", () -> core().allocate(EMPTY)); }
    public void shutdown() { unary("shutdown", () -> core().shutdown(EMPTY)); }
    public CompletableFuture<Void> shutdownAsync() { return unaryAsync("shutdown", () -> core().shutdown(EMPTY)); }

    public void reserve(Duration duration) {
        requirePositive(duration, "duration");
        unary("reserve", () -> core().reserve(cn.arkmillion.agones.internal.proto.Duration.newBuilder()
                .setSeconds(duration.getSeconds()).build()));
    }
    public CompletableFuture<Void> reserveAsync(Duration duration) {
        requirePositive(duration, "duration");
        return unaryAsync("reserve", () -> core().reserve(cn.arkmillion.agones.internal.proto.Duration.newBuilder()
                .setSeconds(duration.getSeconds()).build()));
    }

    /** Sends one complete health stream. Prefer {@link #startHealthPing(Duration)} for production use. */
    public void health() { calls.run("health", () -> { sendOneHealth(); return null; }); }
    public CompletableFuture<Void> healthAsync() { return calls.asyncRun("health", () -> { sendOneHealth(); return null; }); }

    /** Starts a health heartbeat using Agones' conventional two-second interval. */
    public HealthPing startHealthPing() { return startHealthPing(Duration.ofSeconds(2)); }

    public HealthPing startHealthPing(Duration period) {
        requirePositive(period, "period"); ensureOpen();
        StreamingHealthPing ping = new StreamingHealthPing(period.toMillis());
        backgroundTasks.add(ping); ping.start(); return ping;
    }

    public GameServer getGameServer() {
        ensureOpen(); return calls.call("getGameServer", () -> GameServerMapper.fromProto(core().getGameServer(EMPTY)));
    }
    public CompletableFuture<GameServer> getGameServerAsync() {
        ensureOpen(); return calls.async("getGameServer", () -> GameServerMapper.fromProto(core().getGameServer(EMPTY)));
    }

    /** Starts an automatically reconnecting watch. Closing the returned handle cancels it. */
    public AutoCloseable watchGameServer(Consumer<GameServer> callback) {
        Objects.requireNonNull(callback, "callback"); ensureOpen();
        Watch watch = new Watch(callback); backgroundTasks.add(watch); watch.connect(); return watch;
    }

    public void setLabel(String key, String value) { metadata("setLabel", key, value, true); }
    public CompletableFuture<Void> setLabelAsync(String key, String value) { return metadataAsync("setLabel", key, value, true); }
    public void setAnnotation(String key, String value) { metadata("setAnnotation", key, value, false); }
    public CompletableFuture<Void> setAnnotationAsync(String key, String value) { return metadataAsync("setAnnotation", key, value, false); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (AutoCloseable task : backgroundTasks) try { task.close(); } catch (Exception e) { LOG.debug("Background task close failed", e); }
        scheduler.shutdownNow();
        if (ownsChannel) channel.shutdownNow();
        if (ownsExecutor && asyncExecutor instanceof ExecutorService) ((ExecutorService) asyncExecutor).shutdownNow();
        if (shutdownHook != null && Thread.currentThread() != shutdownHook) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); } catch (IllegalStateException ignored) { }
        }
    }

    private SDKGrpc.SDKBlockingStub core() { ensureOpen(); return SDKGrpc.newBlockingStub(channel).withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS); }
    private SDKGrpc.SDKStub asyncCore() { ensureOpen(); return SDKGrpc.newStub(channel).withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS); }
    private void unary(String operation, java.util.concurrent.Callable<?> action) { ensureOpen(); calls.run(operation, action); }
    private CompletableFuture<Void> unaryAsync(String operation, java.util.concurrent.Callable<?> action) { ensureOpen(); return calls.asyncRun(operation, action); }
    private void metadata(String operation, String key, String value, boolean label) {
        validateMetadata(key, value); KeyValue kv = KeyValue.newBuilder().setKey(key).setValue(value).build();
        unary(operation, () -> label ? core().setLabel(kv) : core().setAnnotation(kv));
    }
    private CompletableFuture<Void> metadataAsync(String operation, String key, String value, boolean label) {
        validateMetadata(key, value); KeyValue kv = KeyValue.newBuilder().setKey(key).setValue(value).build();
        return unaryAsync(operation, () -> label ? core().setLabel(kv) : core().setAnnotation(kv));
    }
    private void sendOneHealth() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        StreamObserver<Empty> request = asyncCore().health(new StreamObserver<Empty>() {
            public void onNext(Empty value) { }
            public void onError(Throwable t) { error.set(t); done.countDown(); }
            public void onCompleted() { done.countDown(); }
        });
        request.onNext(EMPTY); request.onCompleted();
        if (!done.await(deadlineMillis + 1000, TimeUnit.MILLISECONDS)) throw new AgonesSdkException("health timed out");
        if (error.get() != null) throw AgonesSdkException.wrap("health", error.get());
    }
    private void ensureOpen() { if (closed.get()) throw new IllegalStateException("AgonesSdk is closed"); }
    private static void validateMetadata(String key, String value) {
        if (key == null || key.trim().isEmpty()) throw new AgonesSdkException("key must not be blank");
        if (value == null) throw new AgonesSdkException("value must not be null");
    }
    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative() || value.getSeconds() < 1) throw new AgonesSdkException(name + " must be at least one second");
    }
    private static ThreadFactory daemonFactory(final String prefix) {
        AtomicInteger id = new AtomicInteger();
        return task -> { Thread thread = new Thread(task, prefix + "-" + id.incrementAndGet()); thread.setDaemon(true); return thread; };
    }

    private final class StreamingHealthPing implements HealthPing {
        private final long periodMillis; private final AtomicBoolean running = new AtomicBoolean(true);
        private volatile StreamObserver<Empty> stream; private volatile ScheduledFuture<?> future;
        StreamingHealthPing(long periodMillis) { this.periodMillis = periodMillis; }
        void start() { future = scheduler.scheduleAtFixedRate(this::tick, 0, periodMillis, TimeUnit.MILLISECONDS); }
        private void tick() {
            if (!running.get()) return;
            try {
                long started = System.nanoTime(); stream().onNext(EMPTY); observer.onSuccess("health", System.nanoTime() - started);
            } catch (Throwable error) {
                stream = null; observer.onFailure("health", 0, error); LOG.warn("Agones health ping failed; retrying", error);
            }
        }
        private synchronized StreamObserver<Empty> stream() {
            if (stream == null) stream = SDKGrpc.newStub(channel).health(new StreamObserver<Empty>() {
                public void onNext(Empty value) { }
                public void onError(Throwable error) { stream = null; observer.onFailure("health", 0, error); LOG.warn("Agones health stream disconnected; retrying", error); }
                public void onCompleted() { stream = null; }
            });
            return stream;
        }
        public void stop() { if (running.compareAndSet(true, false)) { if (future != null) future.cancel(false); StreamObserver<Empty> s = stream; if (s != null) try { s.onCompleted(); } catch (RuntimeException ignored) { } backgroundTasks.remove(this); } }
        public boolean isRunning() { return running.get(); }
    }

    private final class Watch implements AutoCloseable {
        private final Consumer<GameServer> callback; private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicInteger attempts = new AtomicInteger(); private volatile Context.CancellableContext context;
        Watch(Consumer<GameServer> callback) { this.callback = callback; }
        void connect() {
            if (!active.get() || closed.get()) return;
            context = Context.current().withCancellation();
            context.run(() -> SDKGrpc.newStub(channel).watchGameServer(EMPTY, new StreamObserver<cn.arkmillion.agones.internal.proto.GameServer>() {
                public void onNext(cn.arkmillion.agones.internal.proto.GameServer value) {
                    attempts.set(0); try { callback.accept(GameServerMapper.fromProto(value)); } catch (RuntimeException error) { LOG.warn("GameServer watch callback failed", error); }
                }
                public void onError(Throwable error) { reconnect(error); }
                public void onCompleted() { reconnect(null); }
            }));
        }
        private void reconnect(Throwable error) {
            if (!active.get() || closed.get()) return;
            int attempt = Math.min(attempts.incrementAndGet(), 5); long delay = Math.min(1000L << (attempt - 1), 30000L);
            if (error != null) LOG.warn("GameServer watch disconnected; reconnecting in {} ms", delay, error);
            scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        }
        public void close() { if (active.compareAndSet(true, false)) { Context.CancellableContext c = context; if (c != null) c.cancel(null); backgroundTasks.remove(this); } }
    }

    public static final class Builder {
        private String host = env("AGONES_SDK_GRPC_HOST", "localhost");
        private int port = envPort();
        private Duration deadline = Duration.ofSeconds(10);
        private boolean tls, shutdownHook;
        private Executor asyncExecutor;
        private ManagedChannel channel;
        private RpcObserver observer = RpcObserver.NOOP;

        public Builder address(String host, int port) {
            if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("host must not be blank");
            if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be between 1 and 65535");
            this.host = host; this.port = port; return this;
        }
        public Builder deadline(Duration deadline) { requirePositive(deadline, "deadline"); this.deadline = deadline; return this; }
        public Builder tls(boolean tls) { this.tls = tls; return this; }
        public Builder asyncExecutor(Executor executor) { this.asyncExecutor = Objects.requireNonNull(executor, "executor"); return this; }
        public Builder shutdownHook(boolean enabled) { this.shutdownHook = enabled; return this; }
        public Builder observer(RpcObserver observer) { this.observer = Objects.requireNonNull(observer, "observer"); return this; }
        /** Uses a caller-owned channel. Primarily useful for tests and custom transports. */
        public Builder channel(ManagedChannel channel) { this.channel = Objects.requireNonNull(channel, "channel"); return this; }
        public AgonesSdk build() { return new AgonesSdk(this); }
        private static String env(String name, String fallback) { String value = System.getenv(name); return value == null || value.trim().isEmpty() ? fallback : value; }
        private static int envPort() { String value = System.getenv("AGONES_SDK_GRPC_PORT"); if (value == null || value.trim().isEmpty()) return 9357; try { int port = Integer.parseInt(value); return port > 0 && port <= 65535 ? port : 9357; } catch (NumberFormatException ignored) { return 9357; } }
    }
}
