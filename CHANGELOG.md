# Changelog

All notable changes to this project are documented in this file. The format follows Keep a Changelog.

## [Unreleased]

### Added

- Public immutable model types split into focused source files.
- Native gRPC FutureStub-backed asynchronous APIs.
- Restartable `HealthSession` and stateful `WatchHandle` APIs.
- Spring Boot, Micrometer, Flow, and Testcontainers integrations.
- Maven Wrapper, formatting/POM enforcement, protocol provenance checks, ADRs, and API guide.

### Changed

- Default address is `127.0.0.1:9357` and the default unary deadline is 5 seconds.
- Watch reconnects only after transient failures, using bounded exponential backoff with jitter.
- Spring Boot no longer requests GameServer shutdown by default when the application exits.

### Fixed

- Testcontainers configuration tests no longer require a running Docker daemon.

## [1.61.0-0-SNAPSHOT]

- Initial Java 8-compatible SDK implementation for Agones 1.61.x.
- Lifecycle, health, GameServer query/watch, metadata, Counters and Lists APIs.
- Blocking and `CompletableFuture` call styles.
