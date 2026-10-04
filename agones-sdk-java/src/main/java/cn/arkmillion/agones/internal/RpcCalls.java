package cn.arkmillion.agones.internal;

import cn.arkmillion.agones.AgonesSdkException;
import cn.arkmillion.agones.RpcObserver;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class RpcCalls {
    private final Executor executor;
    private final RpcObserver observer;
    public RpcCalls(Executor executor, RpcObserver observer) { this.executor = executor; this.observer = observer; }

    public <T> T call(String operation, Callable<T> action) {
        long started = System.nanoTime();
        try {
            T value = action.call(); observer.onSuccess(operation, System.nanoTime() - started); return value;
        } catch (Throwable error) {
            observer.onFailure(operation, System.nanoTime() - started, error);
            throw AgonesSdkException.wrap(operation, error);
        }
    }
    public void run(String operation, final Callable<?> action) { call(operation, action); }
    public <T> CompletableFuture<T> async(final String operation, final Callable<T> action) {
        return CompletableFuture.supplyAsync(() -> call(operation, action), executor);
    }
    public CompletableFuture<Void> asyncRun(final String operation, final Callable<?> action) {
        return async(operation, () -> { action.call(); return null; });
    }
}
