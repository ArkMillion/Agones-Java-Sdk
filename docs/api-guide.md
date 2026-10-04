# Agones Java SDK 接口使用文档

本文档适用于 `cn.arkmillion.agones:agones-sdk-java:1.61.0-0-SNAPSHOT`，协议目标为 Agones `v1.61.0`。

## 1. 引入依赖

```xml
<dependency>
    <groupId>cn.arkmillion.agones</groupId>
    <artifactId>agones-sdk-java</artifactId>
    <version>1.61.0-0-SNAPSHOT</version>
</dependency>
```

尚未发布 Maven Central 时，在源码目录执行 `./mvnw install`，再由业务项目引用。

## 2. 创建和关闭客户端

零配置创建：

```java
try (AgonesSdk sdk = AgonesSdk.create()) {
    sdk.ready();
}
```

默认值：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| Host | `127.0.0.1` | 可由扩展变量 `AGONES_SDK_GRPC_HOST` 覆盖 |
| Port | `9357` | 可由官方变量 `AGONES_SDK_GRPC_PORT` 覆盖 |
| Deadline | 5 秒 | 只用于一元 RPC 和单次 Health |
| TLS | 关闭 | Pod Sidecar 和本地模式默认 plaintext |
| Shutdown hook | 关闭 | 客户端关闭不会隐式请求 GameServer Shutdown |

完整 Builder：

```java
Executor callbackExecutor = Executors.newFixedThreadPool(2);
AgonesSdk sdk = AgonesSdk.builder()
        .address("127.0.0.1", 9357)
        .deadline(Duration.ofSeconds(5))
        .tls(false)
        .callbackExecutor(callbackExecutor)
        .shutdownHook(false)
        .observer(RpcObserver.NOOP)
        .build();
```

`callbackExecutor` 和通过 `channel(ManagedChannel)` 传入的 channel 归调用方管理；SDK 只关闭自己创建的资源。`close()` 幂等，会停止 Health/Watch，但不会调用 `shutdown()`。

## 3. 生命周期接口

| 同步接口 | 异步接口 | 作用 |
| --- | --- | --- |
| `ready()` | `readyAsync()` | 告知 Agones 游戏服已可接收流量 |
| `allocate()` | `allocateAsync()` | 将当前 GameServer 自分配 |
| `reserve(Duration)` | `reserveAsync(Duration)` | 在指定时间内进入 Reserved 状态 |
| `shutdown()` | `shutdownAsync()` | 请求 Agones 关闭 GameServer |

异步接口返回 `CompletableFuture`，底层直接使用 gRPC FutureStub：

```java
sdk.readyAsync()
        .thenCompose(ignored -> sdk.allocateAsync())
        .whenComplete((ignored, error) -> {
            if (error != null) {
                System.err.println("allocation failed: " + error.getMessage());
            }
        });
```

一元调用不会自动重试。对于写操作，请由业务按幂等性决定是否重试。

## 4. Health

单次探测主要用于诊断：

```java
sdk.health();
CompletableFuture<Void> probe = sdk.healthAsync();
```

生产运行应使用长生命周期会话：

```java
try (HealthSession health = sdk.startHealthSession(Duration.ofSeconds(2))) {
    // 游戏主循环
    if (health.getLastFailure().isPresent()) {
        log.warn("latest health failure", health.getLastFailure().get());
    }
}
```

`HealthSession`：

| 方法 | 说明 |
| --- | --- |
| `start()` | 启动或恢复；重复调用无副作用 |
| `stop()` / `close()` | 停止并结束当前流；重复调用无副作用 |
| `isRunning()` | 是否仍在计划发送心跳 |
| `getLastFailure()` | 最近一次连接/发送失败，可能为空 |

流异常不会终止进程；会话会在下一次周期重新建流。旧接口 `HealthPing` 和 `startHealthPing` 仅为兼容保留，已弃用。

## 5. GameServer 查询和 Watch

查询当前对象：

```java
GameServer gameServer = sdk.getGameServer();
CompletableFuture<GameServer> future = sdk.getGameServerAsync();

String name = gameServer.getObjectMeta().getName();
String state = gameServer.getStatus().getState();
Map<String, Counter> counters = gameServer.getStatus().getCounters();
```

返回模型均位于 `cn.arkmillion.agones.model`，包括 `GameServer`、`ObjectMeta`、`GameServerSpec`、`HealthConfiguration`、`GameServerStatus`、`Address`、`Port`、`PlayerStatus`、`Counter` 和 `GameServerList`。模型与其集合不可变，不会暴露生成的 protobuf 类型。

订阅更新：

```java
WatchHandle watch = sdk.watchGameServer(
        value -> log.info("state={}", value.getStatus().getState()),
        error -> log.warn("watch transport error", error));

WatchState state = watch.getState();
Optional<Throwable> latestError = watch.getLastFailure();
watch.close();
```

状态语义：

| 状态 | 含义 |
| --- | --- |
| `CONNECTING` | 正在创建/重建流 |
| `ACTIVE` | 已收到有效更新 |
| `RETRY_WAIT` | 瞬态错误后等待重连 |
| `CLOSED` | 用户关闭、客户端关闭或不可重试错误 |

Watch 仅对瞬态 gRPC 状态重连，退避约从 100 ms 增长到最多 5 秒并带随机抖动。两个用户回调中的异常都会被隔离。

## 6. Label 和 Annotation

```java
sdk.setLabel("mode", "ranked");
sdk.setAnnotationAsync("build", "2026.10.04").join();
```

| 同步接口 | 异步接口 |
| --- | --- |
| `setLabel(String, String)` | `setLabelAsync(String, String)` |
| `setAnnotation(String, String)` | `setAnnotationAsync(String, String)` |

key 不可为空，value 不可为 `null`。Agones 会在实际元数据 key 前添加 `agones.dev/sdk-`。

## 7. Counters Beta

Counter 必须先在 GameServer 或 Fleet 配置中定义。

```java
CountersApi counters = sdk.counters();
Counter rooms = counters.get("rooms");
long count = counters.getCount("rooms");
long capacity = counters.getCapacity("rooms");

counters.setCount("rooms", 2);
counters.increment("rooms", 1);
counters.decrementAsync("rooms", 1).join();
counters.setCapacityAsync("rooms", 10).join();
```

| 同步 | 异步 | 返回/作用 |
| --- | --- | --- |
| `get(name)` | `getAsync(name)` | 完整 `Counter` |
| `getCount(name)` | `getCountAsync(name)` | 当前 count |
| `setCount(name, value)` | `setCountAsync(...)` | 设置 count |
| `increment(name, amount)` | `incrementAsync(...)` | 增加非负 amount |
| `decrement(name, amount)` | `decrementAsync(...)` | 减少非负 amount |
| `getCapacity(name)` | `getCapacityAsync(name)` | 当前 capacity |
| `setCapacity(name, value)` | `setCapacityAsync(...)` | 设置非负 capacity |

服务端会校验 `0 <= count <= capacity`；不存在的名称通常返回 `NOT_FOUND`。

## 8. Lists Beta

```java
ListsApi lists = sdk.lists();
GameServerList players = lists.get("players");

lists.append("players", "player-42");
boolean present = lists.contains("players", "player-42");
lists.deleteAsync("players", "player-42").join();
lists.setCapacity("players", 100);
lists.setValues("players", Arrays.asList("p1", "p2"));
```

| 同步 | 异步 | 返回/作用 |
| --- | --- | --- |
| `get(name)` | `getAsync(name)` | 完整 `GameServerList` |
| `getCapacity(name)` | `getCapacityAsync(name)` | capacity |
| `setCapacity(name, value)` | `setCapacityAsync(...)` | 设置非负 capacity |
| `contains(name, value)` | `containsAsync(...)` | 是否包含值 |
| `getLength(name)` | `getLengthAsync(name)` | 元素数量 |
| `getValues(name)` | `getValuesAsync(name)` | 不可变值列表 |
| `append(name, value)` | `appendAsync(...)` | 添加一个值 |
| `delete(name, value)` | `deleteAsync(...)` | 删除一个值 |
| `setValues(name, values)` | `setValuesAsync(...)` | 覆盖所有值 |

`setValues` 是覆盖操作；并发修改场景优先使用 `append`/`delete`。

## 9. 异常

```java
try {
    sdk.allocate();
} catch (AgonesSdkException error) {
    Status.Code code = error.getStatusCode();
    String operation = error.getOperation();
    Throwable cause = error.getCause();
}
```

异步错误位于 `CompletionException`/`ExecutionException` 的 cause 中。`AgonesSdkException` 保留 operation、gRPC status 和原始 cause。参数校验失败为 `INVALID_ARGUMENT`；SDK 关闭后调用为 `IllegalStateException`。

## 10. 可选集成

### Micrometer

引入 `agones-sdk-java-micrometer`，然后：

```java
AgonesSdk sdk = AgonesSdk.builder()
        .observer(new MicrometerRpcObserver(meterRegistry))
        .build();
```

指标包括 RPC 时延/结果、Health 心跳/失败、Watch 更新/重连。

### JDK Flow

Java 9+ 引入 `agones-sdk-java-flow`：

```java
Flow.Publisher<GameServer> publisher = new GameServerPublisher(sdk);
publisher.subscribe(subscriber);
```

Publisher 按订阅 demand 投递；取消 subscription 会关闭底层 Watch。

### Spring Boot 2.7

引入 `agones-sdk-java-spring-boot-starter`：

```yaml
agones:
  sdk:
    enabled: true
    host: 127.0.0.1
    port: 9357
    deadline: 5s
    tls: false
    health-enabled: true
    health-period: 2s
    shutdown-on-exit: false
```

Starter 创建 `AgonesSdk` 和生命周期 Bean。`shutdown-on-exit` 是明确的远端关服动作，默认关闭。

### Testcontainers

引入 `agones-sdk-java-testcontainers`：

```java
try (AgonesSdkContainer container = new AgonesSdkContainer()
        .withGameServerConfig(Paths.get("src/test/resources/gameserver.yaml"))) {
    container.start();
    try (AgonesSdk sdk = container.createClient()) {
        sdk.ready();
    }
}
```

该容器固定 Agones 1.61.0 SDK Server 镜像；运行集成测试需要可用的 Docker 环境。
