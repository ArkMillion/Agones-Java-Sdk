# ADR-004: Native asynchronous API

Status: Accepted

Async unary methods adapt gRPC FutureStub results to `CompletableFuture`; they never submit blocking stubs to a worker pool. Cancellation and status-bearing failures propagate.
