# ADR-005: Streaming lifecycle

Status: Accepted

Health and Watch are explicit closeable sessions. Watch retries only transient statuses with jittered 100 ms to 5 second backoff and isolates user callback failures.
