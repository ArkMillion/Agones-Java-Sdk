# Agones Java SDK

[![CI](https://github.com/ArkMillion/Agones-Java-Sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/ArkMillion/Agones-Java-Sdk/actions/workflows/ci.yml)

面向游戏服务器进程的非官方 Agones Java SDK，与 Agones `1.61.x` SDK Server 的 gRPC 协议对齐。核心支持 Java 8，提供同步 API、原生 gRPC 异步 API、Health 长连接、GameServer Watch、Counters/Lists Beta API，以及可选的 Micrometer、Flow、Spring Boot 和 Testcontainers 集成。

当前版本为 `1.61.0-0-SNAPSHOT`，尚未发布到 Maven Central。可先执行 `mvn install` 安装到本地仓库。

## 模块

| Artifact | Java | 用途 |
| --- | ---: | --- |
| `agones-sdk-java` | 8+ | 核心客户端、不可变模型、同步与 `CompletableFuture` API |
| `agones-sdk-java-micrometer` | 8+ | RPC、Health、Watch 指标 |
| `agones-sdk-java-flow` | 9+ | `Flow.Publisher<GameServer>` Watch 适配器 |
| `agones-sdk-java-spring-boot-starter` | 8+ | Spring Boot 2.7 自动装配与生命周期管理 |
| `agones-sdk-java-testcontainers` | 8+ | 固定 Agones 1.61.0 的本地 SDK Server 容器 |
| `examples/simple-game-server` | 8+ | 最小生命周期示例 |

## 安装

```xml
<dependency>
    <groupId>cn.arkmillion.agones</groupId>
    <artifactId>agones-sdk-java</artifactId>
    <version>1.61.0-0-SNAPSHOT</version>
</dependency>
```

## 快速开始

```java
import cn.arkmillion.agones.AgonesSdk;
import cn.arkmillion.agones.HealthSession;
import cn.arkmillion.agones.WatchHandle;

try (AgonesSdk sdk = AgonesSdk.create();
     HealthSession health = sdk.startHealthSession();
     WatchHandle watch = sdk.watchGameServer(
             gameServer -> System.out.println(gameServer.getStatus().getState()),
             error -> System.err.println("watch: " + error.getMessage()))) {
    sdk.ready();
    sdk.counters().increment("rooms", 1);
    sdk.lists().append("players", "player-42");
    sdk.allocateAsync().join();
}
```

`close()` 只关闭客户端持有的资源，不会自动调用 Agones `Shutdown`。需要关服时请显式调用 `sdk.shutdown()`。

## 配置与线程模型

默认连接 `127.0.0.1:9357`，一元 RPC deadline 为 5 秒，使用 plaintext。`AGONES_SDK_GRPC_PORT` 是 Agones 官方注入变量；`AGONES_SDK_GRPC_HOST` 是本项目提供的扩展，便于本地和特殊网络环境配置。

```java
AgonesSdk sdk = AgonesSdk.builder()
        .address("127.0.0.1", 9357)
        .deadline(Duration.ofSeconds(5))
        .tls(false)
        .shutdownHook(false)
        .build();
```

- 客户端与各 API facade 是线程安全的。
- 异步一元调用直接使用 gRPC `FutureStub`，不会在线程池中包装阻塞调用。
- Builder 的 `callbackExecutor` 只执行异步完成映射；调用方提供时，其生命周期仍归调用方。
- SDK 使用一个 daemon scheduler 管理 Health 与 Watch；`close()` 会停止后台任务并有限等待自有 channel/executor 退出。
- 一元 RPC 不做自动重试，避免对生命周期和写操作产生重复副作用。

## API 覆盖

| 能力 | 同步 | 异步 | 说明 |
| --- | --- | --- | --- |
| Ready / Allocate / Shutdown / Reserve | 是 | 是 | 与官方稳定 SDK RPC 一致 |
| SetLabel / SetAnnotation | 是 | 是 | Agones 会添加 `agones.dev/sdk-` 前缀 |
| GetGameServer | 是 | 是 | 返回 SDK 自有不可变模型 |
| Health | 单次 | 单次 | `HealthSession` 提供生产用长连接 |
| WatchGameServer | 流 | — | `WatchHandle` 可查询状态与最近错误 |
| Counters Beta | 是 | 是 | get/count/capacity/increment/decrement |
| Lists Beta | 是 | 是 | get/capacity/contains/append/delete/setValues |

Counters 和 Lists 名称必须预先在 GameServer/Fleet 中定义。Beta API 可能随 Agones 演进，升级前请查看 Changelog。

## Health 与 Watch

`HealthSession` 支持幂等的 `start()`、`stop()`、`close()`，并暴露 `isRunning()` 和 `getLastFailure()`。流断开后，下一次心跳会重新建立连接；错误会记录并上报观察器，不会终止游戏进程。

`WatchHandle` 的状态为 `CONNECTING`、`ACTIVE`、`RETRY_WAIT` 或 `CLOSED`。只有 `UNAVAILABLE`、`DEADLINE_EXCEEDED`、`RESOURCE_EXHAUSTED`、`ABORTED`、`INTERNAL`、`UNKNOWN` 会重试；退避从约 100 ms 指数增长到最多 5 秒并带抖动。用户数据回调和错误回调抛出的异常不会破坏传输线程。

## 错误处理

一元 RPC 失败会抛出或异步完成为 `AgonesSdkException`。异常保留原始 cause、gRPC `Status.Code` 和 operation：

```java
try {
    sdk.ready();
} catch (AgonesSdkException error) {
    System.err.println(error.getOperation() + ": " + error.getStatusCode());
}
```

参数错误使用 `INVALID_ARGUMENT`。在客户端关闭后继续调用会得到 `IllegalStateException`。

## Micrometer

```java
AgonesSdk sdk = AgonesSdk.builder()
        .observer(new MicrometerRpcObserver(meterRegistry))
        .build();
```

提供 `agones.sdk.rpc.duration`、`agones.sdk.rpc.calls`、`agones.sdk.health.pings`、`agones.sdk.health.failures`、`agones.sdk.watch.updates` 和 `agones.sdk.watch.reconnects`。

## Spring Boot

加入 `agones-sdk-java-spring-boot-starter` 后会创建 `AgonesSdk` Bean，并可自动启动 Health：

```yaml
agones:
  sdk:
    host: 127.0.0.1
    port: 9357
    deadline: 5s
    health-enabled: true
    health-period: 2s
    shutdown-on-exit: false
```

`shutdown-on-exit` 默认为 `false`；只有明确希望应用停止时请求 Agones Shutdown 才应启用。设置 `agones.sdk.enabled=false` 可关闭自动配置。自定义 `AgonesSdk`、`ManagedChannel` 或 `RpcObserver` Bean 会被复用。

## Testcontainers 与本地开发

```java
try (AgonesSdkContainer sidecar = new AgonesSdkContainer()) {
    sidecar.start();
    try (AgonesSdk sdk = sidecar.createClient()) {
        sdk.ready();
    }
}
```

容器固定使用 `us-docker.pkg.dev/agones-images/release/agones-sdk:1.61.0`，以 `--local --address 0.0.0.0` 启动。`withGameServerConfig(path)` 可加载定义 Counters/Lists 的 GameServer YAML。也可以直接运行官方 `sdk-server --local`，再用默认客户端连接。

## 构建、测试与贡献

```bash
./mvnw verify
```

核心测试使用 gRPC in-process server，不依赖 Docker 或 Kubernetes。CI 在 Java 8 上验证核心，并在 Java 11、17、21 上验证整个 reactor。代码格式由 Spotless/Google Java Format 校验，POM 由 SortPom 校验。协议来源可运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-proto-provenance.ps1
```

完整接口用法见 [`docs/api-guide.md`](docs/api-guide.md)，架构决策见 [`docs/adr`](docs/adr/README.md)，协议来源见 [`docs/proto-provenance.md`](docs/proto-provenance.md)。

## 兼容性与版本

| SDK 版本 | 目标 Agones | Java |
| --- | --- | --- |
| `1.61.0-0` | `1.61.x`（wire source: `v1.61.0`） | 核心 8/11/17/21；Flow 9+ |

版本号采用 `{agones-version}-{sdk-patch}`。协议文件仅移除了与 gRPC wire contract 无关的 HTTP/OpenAPI 注解；RPC 形状和字段编号由来源校验脚本检查。项目采用 Apache License 2.0。
