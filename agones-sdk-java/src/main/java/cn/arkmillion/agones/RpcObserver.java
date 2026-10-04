package cn.arkmillion.agones;

/** Optional observation hook used by integrations such as Micrometer. */
public interface RpcObserver {
    RpcObserver NOOP = new RpcObserver() {
        public void onSuccess(String operation, long elapsedNanos) { }
        public void onFailure(String operation, long elapsedNanos, Throwable error) { }
    };
    void onSuccess(String operation, long elapsedNanos);
    void onFailure(String operation, long elapsedNanos, Throwable error);
}

