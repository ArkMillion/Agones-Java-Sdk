package cn.arkmillion.agones.internal;

import cn.arkmillion.agones.AgonesSdkException;
import cn.arkmillion.agones.RpcObserver;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** Centralizes error translation and observation for blocking and native gRPC future calls. */
public final class RpcCalls {
  private final Executor callbackExecutor;
  private final RpcObserver observer;

  public RpcCalls(Executor callbackExecutor, RpcObserver observer) {
    this.callbackExecutor = callbackExecutor;
    this.observer = observer;
  }

  public <T> T call(String operation, Callable<T> action) {
    long started = System.nanoTime();
    try {
      T value = action.call();
      observer.onSuccess(operation, System.nanoTime() - started);
      return value;
    } catch (Throwable error) {
      observer.onFailure(operation, System.nanoTime() - started, error);
      throw AgonesSdkException.wrap(operation, error);
    }
  }

  public void run(String operation, final Callable<?> action) {
    call(operation, action);
  }

  public <T, R> CompletableFuture<R> future(
      String operation, Callable<ListenableFuture<T>> action, Function<T, R> mapper) {
    final long started = System.nanoTime();
    final ListenableFuture<T> grpcFuture;
    try {
      grpcFuture = action.call();
    } catch (Throwable error) {
      observer.onFailure(operation, System.nanoTime() - started, error);
      CompletableFuture<R> failed = new CompletableFuture<R>();
      failed.completeExceptionally(AgonesSdkException.wrap(operation, error));
      return failed;
    }
    final CompletableFuture<R> result = new CompletableFuture<R>();
    grpcFuture.addListener(
        () -> {
          try {
            result.complete(mapper.apply(grpcFuture.get()));
            observer.onSuccess(operation, System.nanoTime() - started);
          } catch (Throwable rawError) {
            Throwable error =
                rawError instanceof ExecutionException && rawError.getCause() != null
                    ? rawError.getCause()
                    : rawError;
            observer.onFailure(operation, System.nanoTime() - started, error);
            result.completeExceptionally(AgonesSdkException.wrap(operation, error));
          }
        },
        callbackExecutor);
    result.whenComplete(
        (value, error) -> {
          if (result.isCancelled()) grpcFuture.cancel(true);
        });
    return result;
  }

  public <T> CompletableFuture<T> future(String operation, Callable<ListenableFuture<T>> action) {
    return future(operation, action, Function.identity());
  }

  public <T> CompletableFuture<Void> futureRun(
      String operation, Callable<ListenableFuture<T>> action) {
    return future(operation, action, value -> null);
  }

  public <T> CompletableFuture<T> observe(String operation, CompletableFuture<T> future) {
    final long started = System.nanoTime();
    future.whenComplete(
        (value, error) -> {
          if (error == null) observer.onSuccess(operation, System.nanoTime() - started);
          else observer.onFailure(operation, System.nanoTime() - started, error);
        });
    return future;
  }
}
