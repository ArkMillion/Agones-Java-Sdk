# Agones Java SDK

面向游戏服务器进程的 Agones Java SDK，与 Agones `1.61.x` SDK Server 的 gRPC 协议对齐。项目以 Java 8 为核心基线，同时提供同步与 `CompletableFuture` API、自动健康心跳、Watch 自动重连，以及完整的 Counters / Lists Beta 能力。

> 当前版本为 `1.61.0-0-SNAPSHOT`，尚未发布到 Maven Central。可先运行 `mvn install` 安装到本地仓库。

## 模块

| Artifact | Java | 用途 |
| --- | ---: | --- |
| `agones-sdk-java` | 8+ | 核心 SDK、不可变模型、同步/异步 API |
| `agones-sdk-java-micrometer` | 8+ | RPC 时延、调用结果与健康失败指标 |
| `agones-sdk-java-flow` | 9+ | `Flow.Publisher<GameServer>` Watch 适配器 |
| `simple-game-server` | 8+ | 最小生命周期示例 |

## 快速开始

```xml
<dependency>
  <groupId>cn.arkmillion.agones</groupId>
  <artifactId>agones-sdk-java</artifactId>
  <version>1.61.0-0-SNAPSHOT</version>
</dependency>
```

```java
import cn.arkmillion.agones.AgonesSdk;
import cn.arkmillion.agones.HealthPing;
import java.time.Duration;

try (AgonesSdk sdk = AgonesSdk.connect();
     HealthPing ping = sdk.startHealthPing()) {
    sdk.watchGameServer(gs -> System.out.println(gs.getStatus().getState()));
    sdk.ready();
    sdk.counters().increment("rooms", 1);
    sdk.lists().append("players", "player-42");
    sdk.allocateAsync().join();
    sdk.shutdown();
}
```

SDK 默认读取 `AGONES_SDK_GRPC_HOST`（缺省 `localhost`）和 `AGONES_SDK_GRPC_PORT`（缺省 `9357`）。本地开发可直接连接 `sdk-server --local`；也可以显式配置：

```java
AgonesSdk sdk = AgonesSdk.builder()
    .address("127.0.0.1", 9357)
    .deadline(Duration.ofSeconds(5))
    .tls(false)
    .shutdownHook(true)
    .build();
```

Agones 会自动给 SDK 写入的 Label 与 Annotation 加上 `agones.dev/sdk-` 前缀。Counters 和 Lists 的 key 必须预先定义在 GameServer/Fleet 配置中。

## Micrometer

```java
AgonesSdk sdk = AgonesSdk.builder()
    .observer(new MicrometerRpcObserver(meterRegistry))
    .build();
```

模块暴露 `agones.sdk.rpc.duration`、`agones.sdk.rpc.calls` 和 `agones.sdk.health.failures`。

## 构建与测试

```bash
mvn verify
```

核心测试使用 gRPC in-process server，不依赖 Kubernetes。真实 Sidecar 契约测试可将示例连接到 Agones `sdk-server --local` 执行。

协议文件派生自 Agones v1.61.0，并仅移除了与 gRPC wire contract 无关的 HTTP/OpenAPI 注解。项目采用 Apache License 2.0。

## 兼容性

| SDK 版本 | Agones | Java |
| --- | --- | --- |
| `1.61.0-0` | `1.59.x`–`1.61.x`（目标版本 `1.61.0`） | 核心 8/11/17/21；Flow 模块 9+ |

版本号采用 `{agones-version}-{sdk-patch}`。Beta Counters/Lists 会随 Agones 协议演进，升级前请查看本项目 Changelog。

