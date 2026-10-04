package cn.arkmillion.agones;

import cn.arkmillion.agones.internal.GameServerMapper;
import cn.arkmillion.agones.internal.RpcCalls;
import cn.arkmillion.agones.internal.proto.Empty;
import cn.arkmillion.agones.internal.proto.KeyValue;
import cn.arkmillion.agones.internal.proto.SDKGrpc;
import cn.arkmillion.agones.model.GameServer;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private final Executor callbackExecutor;
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
            channel = channelBuilder.build();
            ownsChannel = true;
        } else {
            channel = builder.channel;
            ownsChannel = false;
        }
        deadlineMillis = builder.deadline.toMillis();
        if (builder.callbackExecutor == null) {
            callbackExecutor = Executors.newCachedThreadPool(daemonFactory("agones-callback"));
            ownsExecutor = true;
        } else {
            callbackExecutor = builder.callbackExecutor;
            ownsExecutor = false;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("agones-scheduler"));
        observer = builder.observer;
        calls = new RpcCalls(callbackExecutor, observer);
        counters = new CountersApi(
                () -> cn.arkmillion.agones.internal.proto.beta.SDKGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS),
                () -> cn.arkmillion.agones.internal.proto.beta.SDKGrpc.newFutureStub(channel)
                        .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS), calls);
        lists = new ListsApi(
                () -> cn.arkmillion.agones.internal.proto.beta.SDKGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS),
                () -> cn.arkmillion.agones.internal.proto.beta.SDKGrpc.newFutureStub(channel)
                        .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS), calls);
        if (builder.shutdownHook) {
            shutdownHook = new Thread(this::close, "agones-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } else {
            shutdownHook = null;
        }
    }

    public static AgonesSdk create() { return builder().build(); }
    public static AgonesSdk connect() { return create(); }
    public static Builder builder() { return new Builder(); }
    public CountersApi counters() { ensureOpen(); return counters; }
    public ListsApi lists() { ensureOpen(); return lists; }

    public void ready() { unary("ready", () -> core().ready(EMPTY)); }
    public CompletableFuture<Void> readyAsync() { return calls.futureRun("ready", () -> futureCore().ready(EMPTY)); }
    public void allocate() { unary("allocate", () -> core().allocate(EMPTY)); }
    public CompletableFuture<Void> allocateAsync() { return calls.futureRun("allocate", () -> futureCore().allocate(EMPTY)); }
    public void shutdown() { unary("shutdown", () -> core().shutdown(EMPTY)); }
    public CompletableFuture<Void> shutdownAsync() { return calls.futureRun("shutdown", () -> futureCore().shutdown(EMPTY)); }

    public void reserve(Duration duration) {
        requirePositive(duration, "duration");
        unary("reserve", () -> core().reserve(protoDuration(duration)));
    }

    public CompletableFuture<Void> reserveAsync(Duration duration) {
        requirePositive(duration, "duration");
        return calls.futureRun("reserve", () -> futureCore().reserve(protoDuration(duration)));
    }

    /** Sends one health ping on a short-lived stream. */
    public void health() {
        try {
            healthAsync().get(deadlineMillis + 1000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException error) {
            throw AgonesSdkException.wrap("health", error.getCause());
        } catch (TimeoutException error) {
            throw new AgonesSdkException("health", Status.DEADLINE_EXCEEDED.asRuntimeException());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AgonesSdkException("health", error);
        }
    }

    /** Sends one health ping without occupying a blocking worker thread. */
    public CompletableFuture<Void> healthAsync() {
        ensureOpen();
        CompletableFuture<Void> result = new CompletableFuture<Void>();
        AtomicReference<StreamObserver<Empty>> requestRef = new AtomicReference<StreamObserver<Empty>>();
        StreamObserver<Empty> request = asyncCoreWithDeadline().health(new StreamObserver<Empty>() {
            public void onNext(Empty value) { }
            public void onError(Throwable error) { result.completeExceptionally(AgonesSdkException.wrap("health", error)); }
            public void onCompleted() { result.complete(null); }
        });
        requestRef.set(request);
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                StreamObserver<Empty> active = requestRef.get();
                if (active != null) active.onError(Status.CANCELLED.asRuntimeException());
            }
        });
        request.onNext(EMPTY);
        request.onCompleted();
        return calls.observe("health", result);
    }

    public HealthSession startHealthSession() { return startHealthSession(Duration.ofSeconds(2)); }

    public HealthSession startHealthSession(Duration period) {
        requirePositive(period, "period");
        ensureOpen();
        StreamingHealthSession session = new StreamingHealthSession(period.toMillis());
        backgroundTasks.add(session);
        session.start();
        return session;
    }

    /** @deprecated use {@link #startHealthSession(Duration)}. */
    @Deprecated
    public HealthPing startHealthPing() { return startHealthPing(Duration.ofSeconds(2)); }

    /** @deprecated use {@link #startHealthSession(Duration)}. */
    @Deprecated
    public HealthPing startHealthPing(Duration period) {
        requirePositive(period, "period");
        ensureOpen();
        StreamingHealthSession session = new StreamingHealthSession(period.toMillis());
        backgroundTasks.add(session);
        session.start();
        return session;
    }

    public GameServer getGameServer() {
        ensureOpen();
        return calls.call("getGameServer", () -> GameServerMapper.fromProto(core().getGameServer(EMPTY)));
    }

    public CompletableFuture<GameServer> getGameServerAsync() {
        ensureOpen();
        return calls.future("getGameServer", () -> futureCore().getGameServer(EMPTY), GameServerMapper::fromProto);
    }

    public WatchHandle watchGameServer(Consumer<GameServer> callback) {
        return watchGameServer(callback, error -> LOG.warn("GameServer watch failed", error));
    }

    /** Starts a watch that retries transient gRPC failures with bounded exponential backoff. */
    public WatchHandle watchGameServer(Consumer<GameServer> callback, Consumer<Throwable> errorCallback) {
        Objects.requireNonNull(callback, "callback");
        Objects.requireNonNull(errorCallback, "errorCallback");
        ensureOpen();
        Watch watch = new Watch(callback, errorCallback);
        backgroundTasks.add(watch);
        watch.connect();
        return watch;
    }

    public void setLabel(String key, String value) { metadata("setLabel", key, value, true); }
    public CompletableFuture<Void> setLabelAsync(String key, String value) { return metadataAsync("setLabel", key, value, true); }
    public void setAnnotation(String key, String value) { metadata("setAnnotation", key, value, false); }
    public CompletableFuture<Void> setAnnotationAsync(String key, String value) { return metadataAsync("setAnnotation", key, value, false); }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (AutoCloseable task : backgroundTasks) {
            try { task.close(); } catch (Exception error) { LOG.debug("Background task close failed", error); }
        }
        scheduler.shutdownNow();
        awaitTermination(scheduler, 1, TimeUnit.SECONDS);
        if (ownsChannel) {
            channel.shutdown();
            if (!awaitTermination(channel, Math.min(deadlineMillis, 5000), TimeUnit.MILLISECONDS)) channel.shutdownNow();
        }
        if (ownsExecutor && callbackExecutor instanceof ExecutorService) {
            ExecutorService service = (ExecutorService) callbackExecutor;
            service.shutdown();
            if (!awaitTermination(service, 1, TimeUnit.SECONDS)) service.shutdownNow();
        }
        if (shutdownHook != null && Thread.currentThread() != shutdownHook) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); } catch (IllegalStateException ignored) { }
        }
    }

    private SDKGrpc.SDKBlockingStub core() {
        ensureOpen();
        return SDKGrpc.newBlockingStub(channel).withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS);
    }

    private SDKGrpc.SDKFutureStub futureCore() {
        ensureOpen();
        return SDKGrpc.newFutureStub(channel).withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS);
    }

    private SDKGrpc.SDKStub asyncCoreWithDeadline() {
        ensureOpen();
        return SDKGrpc.newStub(channel).withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS);
    }

    private void unary(String operation, java.util.concurrent.Callable<?> action) {
        ensureOpen();
        calls.run(operation, action);
    }

    private void metadata(String operation, String key, String value, boolean label) {
        validateMetadata(key, value);
        KeyValue request = KeyValue.newBuilder().setKey(key).setValue(value).build();
        unary(operation, () -> label ? core().setLabel(request) : core().setAnnotation(request));
    }

    private CompletableFuture<Void> metadataAsync(String operation, String key, String value, boolean label) {
        validateMetadata(key, value);
        KeyValue request = KeyValue.newBuilder().setKey(key).setValue(value).build();
        return calls.futureRun(operation, () -> label
                ? futureCore().setLabel(request) : futureCore().setAnnotation(request));
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("AgonesSdk is closed");
    }

    private static cn.arkmillion.agones.internal.proto.Duration protoDuration(Duration duration) {
        return cn.arkmillion.agones.internal.proto.Duration.newBuilder().setSeconds(duration.getSeconds()).build();
    }

    private static void validateMetadata(String key, String value) {
        if (key == null || key.trim().isEmpty()) throw new AgonesSdkException("key must not be blank");
        if (value == null) throw new AgonesSdkException("value must not be null");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative() || value.getSeconds() < 1) {
            throw new AgonesSdkException(name + " must be at least one second");
        }
    }

    private static ThreadFactory daemonFactory(final String prefix) {
        AtomicInteger id = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + "-" + id.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static boolean awaitTermination(ExecutorService service, long timeout, TimeUnit unit) {
        try { return service.awaitTermination(timeout, unit); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); return false; }
    }

    private static boolean awaitTermination(ManagedChannel channel, long timeout, TimeUnit unit) {
        try { return channel.awaitTermination(timeout, unit); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); return false; }
    }

    private final class StreamingHealthSession implements HealthPing {
        private final long periodMillis;
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicReference<Throwable> lastFailure = new AtomicReference<Throwable>();
        private volatile StreamObserver<Empty> stream;
        private volatile ScheduledFuture<?> future;

        StreamingHealthSession(long periodMillis) { this.periodMillis = periodMillis; }

        public synchronized void start() {
            ensureOpen();
            if (!running.compareAndSet(false, true)) return;
            if (!backgroundTasks.contains(this)) backgroundTasks.add(this);
            future = scheduler.scheduleAtFixedRate(this::tick, 0, periodMillis, TimeUnit.MILLISECONDS);
        }

        private void tick() {
            if (!running.get() || closed.get()) return;
            try {
                long started = System.nanoTime();
                getOrCreateStream().onNext(EMPTY);
                observer.onSuccess("health.ping", System.nanoTime() - started);
            } catch (Throwable error) {
                fail(error);
                synchronized (this) { stream = null; }
            }
        }

        private synchronized StreamObserver<Empty> getOrCreateStream() {
            if (stream == null) {
                stream = SDKGrpc.newStub(channel).health(new StreamObserver<Empty>() {
                    public void onNext(Empty value) { }
                    public void onError(Throwable error) { disconnected(error); }
                    public void onCompleted() { disconnected(Status.UNAVAILABLE.withDescription("health stream completed").asRuntimeException()); }
                });
            }
            return stream;
        }

        private synchronized void disconnected(Throwable error) {
            if (!running.get()) return;
            stream = null;
            fail(error);
        }

        private void fail(Throwable error) {
            lastFailure.set(error);
            observer.onFailure("health.ping", 0, error);
            LOG.warn("Agones health stream failed; the next ping will reconnect", error);
        }

        public synchronized void stop() {
            if (!running.compareAndSet(true, false)) return;
            if (future != null) future.cancel(false);
            StreamObserver<Empty> active = stream;
            stream = null;
            if (active != null) {
                try { active.onCompleted(); } catch (RuntimeException ignored) { }
            }
            backgroundTasks.remove(this);
        }

        public boolean isRunning() { return running.get(); }
        public Optional<Throwable> getLastFailure() { return Optional.ofNullable(lastFailure.get()); }
    }

    private final class Watch implements WatchHandle {
        private final Consumer<GameServer> callback;
        private final Consumer<Throwable> errorCallback;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicReference<WatchState> state = new AtomicReference<WatchState>(WatchState.CONNECTING);
        private final AtomicReference<Throwable> lastFailure = new AtomicReference<Throwable>();
        private volatile Context.CancellableContext context;
        private volatile ScheduledFuture<?> retry;

        Watch(Consumer<GameServer> callback, Consumer<Throwable> errorCallback) {
            this.callback = callback;
            this.errorCallback = errorCallback;
        }

        void connect() {
            if (!active.get() || closed.get()) return;
            state.set(WatchState.CONNECTING);
            context = Context.current().withCancellation();
            context.run(() -> SDKGrpc.newStub(channel).watchGameServer(EMPTY,
                    new StreamObserver<cn.arkmillion.agones.internal.proto.GameServer>() {
                        public void onNext(cn.arkmillion.agones.internal.proto.GameServer value) {
                            attempts.set(0);
                            state.set(WatchState.ACTIVE);
                            try { callback.accept(GameServerMapper.fromProto(value)); }
                            catch (Throwable error) { LOG.warn("GameServer watch callback failed", error); }
                        }
                        public void onError(Throwable error) { disconnected(error); }
                        public void onCompleted() {
                            disconnected(Status.UNAVAILABLE.withDescription("watch stream completed").asRuntimeException());
                        }
                    }));
        }

        private void disconnected(Throwable error) {
            if (!active.get() || closed.get()) return;
            lastFailure.set(error);
            notifyError(error);
            if (!isTransient(error)) {
                close();
                return;
            }
            int attempt = Math.min(attempts.incrementAndGet(), 16);
            long baseDelay = Math.min(100L << Math.min(attempt - 1, 6), 5000L);
            long delay = Math.min(5000L, baseDelay + ThreadLocalRandom.current().nextLong(Math.max(1L, baseDelay / 4L)));
            state.set(WatchState.RETRY_WAIT);
            observer.onFailure("watch.reconnect", 0, error);
            retry = scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        }

        private void notifyError(Throwable error) {
            try { errorCallback.accept(AgonesSdkException.wrap("watchGameServer", error)); }
            catch (Throwable callbackError) { LOG.warn("GameServer watch error callback failed", callbackError); }
        }

        private boolean isTransient(Throwable error) {
            Status.Code code = Status.fromThrowable(error).getCode();
            return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED
                    || code == Status.Code.RESOURCE_EXHAUSTED || code == Status.Code.ABORTED
                    || code == Status.Code.INTERNAL || code == Status.Code.UNKNOWN;
        }

        public WatchState getState() { return state.get(); }
        public Optional<Throwable> getLastFailure() { return Optional.ofNullable(lastFailure.get()); }
        public boolean isClosed() { return !active.get(); }

        public void close() {
            if (!active.compareAndSet(true, false)) return;
            state.set(WatchState.CLOSED);
            ScheduledFuture<?> scheduled = retry;
            if (scheduled != null) scheduled.cancel(false);
            Context.CancellableContext current = context;
            if (current != null) current.cancel(null);
            backgroundTasks.remove(this);
        }
    }

    /** Builder for {@link AgonesSdk}. */
    public static final class Builder {
        private String host = env("AGONES_SDK_GRPC_HOST", "127.0.0.1");
        private int port = envPort();
        private Duration deadline = Duration.ofSeconds(5);
        private boolean tls;
        private boolean shutdownHook;
        private Executor callbackExecutor;
        private ManagedChannel channel;
        private RpcObserver observer = RpcObserver.NOOP;

        public Builder address(String host, int port) {
            if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("host must not be blank");
            if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be between 1 and 65535");
            this.host = host;
            this.port = port;
            return this;
        }

        public Builder deadline(Duration deadline) { requirePositive(deadline, "deadline"); this.deadline = deadline; return this; }
        public Builder tls(boolean tls) { this.tls = tls; return this; }
        public Builder callbackExecutor(Executor executor) { this.callbackExecutor = Objects.requireNonNull(executor, "executor"); return this; }
        /** @deprecated use {@link #callbackExecutor(Executor)}. */
        @Deprecated public Builder asyncExecutor(Executor executor) { return callbackExecutor(executor); }
        public Builder shutdownHook(boolean enabled) { this.shutdownHook = enabled; return this; }
        public Builder observer(RpcObserver observer) { this.observer = Objects.requireNonNull(observer, "observer"); return this; }
        /** Uses a caller-owned channel. Primarily useful for tests and custom transports. */
        public Builder channel(ManagedChannel channel) { this.channel = Objects.requireNonNull(channel, "channel"); return this; }
        public AgonesSdk build() { return new AgonesSdk(this); }

        private static String env(String name, String fallback) {
            String value = System.getenv(name);
            return value == null || value.trim().isEmpty() ? fallback : value;
        }

        private static int envPort() {
            String value = System.getenv("AGONES_SDK_GRPC_PORT");
            if (value == null || value.trim().isEmpty()) return 9357;
            try {
                int port = Integer.parseInt(value);
                return port > 0 && port <= 65535 ? port : 9357;
            } catch (NumberFormatException ignored) {
                return 9357;
            }
        }
    }
}
