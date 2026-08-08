# Pole Java

`pole-java` 是 Pole 的 Java 客户端运行时 monorepo，统一承载 Thin SDK 核心、
framework adapter 与薄 Java Agent。现有 Maven artifact
`io.github.lattice-hub:pole-client-java` 保持为 Thin SDK 核心入口。

当前已实现的 Thin SDK 负责：

- 通过 `OpenControlSession` gRPC over Unix Domain Socket 与本地 Pole Sidecar 建立长期内部会话；
- 接收 Sidecar 首帧主动下发的 HTTP、gRPC、Dubbo、Thrift listener 端口；
- 注册本地 ingress 服务，并读取 Sidecar 回传的注册状态；
- 向框架 adapter 提供线程安全的本地 listener 地址；
- 构造仅含 `namespace`、`service` 的不可变 `TargetService`；
- 生成业务协议使用的 canonical target service 元信息。
- 传播可选的 `TrafficContext` 流量标签，不以 trace-id 关联灰度。

## 模块

| 模块 | 职责 | 发布状态 |
| --- | --- | --- |
| `pole-client-java` | Thin SDK 核心、TrafficContext、UDS 控制会话和本地服务注册 | 已实现 |
| `pole-java-bom` | Java 模块统一版本管理 | 已实现 |
| `adapters/spring-cloud/pole-spring-cloud-common` | Spring Cloud LoadBalancer 共用出站行为 | 已实现 |
| `adapters/spring-cloud/pole-spring-cloud-boot-{2,3,4}` | 按 Boot 主版本隔离的自动安装与入站适配 | 已实现 |
| `agent/pole-agent-api` | framework-neutral Agent 插件 SPI | 已实现 |
| `agent/pole-agent-core` | 插件发现、生命周期和应用 ClassLoader payload 隔离 | 已实现 |
| `agent/plugins/pole-agent-plugin-spring-cloud` | Spring Cloud 探测与 Boot 2/3/4 adapter 选择 | 已实现 |
| `agent/pole-java-agent` | 内置插件的单一 shaded Agent JAR 分发 | 已实现 |
| `agent/docker` | 可作为 init container 使用的 Agent artifact image | 已实现 |

核心类继续位于同一个 artifact，避免将既有 `io.github.latticehub.client` package
拆散到多个 JAR。adapter 是请求级行为的唯一实现；Agent 不复制目标身份、TrafficContext 或 Sidecar
连接逻辑，也不通过逐 RPC 字节码增强重复治理能力。

业务请求不经过 bootstrap gRPC 会话。框架 adapter 应把请求发送到对应协议的
`127.0.0.1:{listenerPort}`，并注入：

```text
latticehub-target-namespace
latticehub-target-service
```

## 要求

- Java 17 或更高版本；
- Maven 3.8 或更高版本；
- macOS 或 Linux 等支持 JDK Unix Domain Socket 的 Unix-like 系统；
- Sidecar 与业务容器共享 `/var/run/pole/sidecar` 目录。

SDK 使用 gRPC Java 官方 Netty transport，并通过 Java 17 的
`UnixDomainSocketAddress` 与 Netty NIO domain socket channel 建连。无需额外安装
Epoll/KQueue native transport，也没有平台 classifier 运行时依赖。Windows 不在 v1
支持范围内。

## Bootstrap

默认 UDS：

```text
/var/run/pole/sidecar/bootstrap.sock
```

仅本地开发、测试或特殊部署可通过 `POLE_SIDECAR_SOCKET` 覆盖。SDK 不内置任何
业务 listener 端口。

```java
import io.github.latticehub.client.SidecarBootstrapClient;
import io.github.latticehub.client.SidecarProtocol;
import java.net.InetSocketAddress;

try (SidecarBootstrapClient sidecar = SidecarBootstrapClient.connect()) {
    InetSocketAddress grpcAddress = sidecar.listenerAddress(SidecarProtocol.GRPC);
    // 把业务 gRPC channel 指向 grpcAddress。
}
```

`connect()` 在初始化阶段有界等待并使用指数退避重试。可按应用启动预算覆盖参数：

```java
import java.time.Duration;

SidecarBootstrapClient sidecar = SidecarBootstrapClient.builder()
        .initializationTimeout(Duration.ofSeconds(20))
        .initialBackoff(Duration.ofMillis(100))
        .maxBackoff(Duration.ofSeconds(2))
        .connect();
```

Sidecar 必须把完整 listener 表作为 `OpenControlSession` 首帧发送，客户端首个事件为
`ClientHello`。SDK 验证四种协议、端口范围和协议唯一性后原子安装不可变快照。会话断开时快照立即失效，
`listenerAddress` 和 `listenerAddresses` 快速抛出 `SidecarUnavailableException`；
后台继续指数退避重连，新会话首帧通过后才恢复；未注销的本地服务注册会在重连后重放。

```java
LocalServiceRegistration registration = sidecar.registerLocalService(
        "payments-http", "prod", "payments", SidecarProtocol.HTTP, 8080);
sidecar.localServiceStatus(registration.getRegistrationId()).ifPresent(System.out::println);
sidecar.unregisterLocalService(registration.getRegistrationId());
```

## Target Service

```java
import io.github.latticehub.client.TargetService;
import io.github.latticehub.client.TargetServiceMetadata;
import java.util.Map;

TargetService target = TargetService.builder()
        .namespace("default")
        .service("orders")
        .build();

Map<String, String> metadata = TargetServiceMetadata.encode(target);
```

`TargetServiceMetadata.encode(existingMetadata, target)` 会按大小写不敏感方式删除调用方
已有的同名内部字段，再写入权威值，并返回不可修改 Map。值使用 canonical UTF-8
`%HH` 编码。

框架 adapter 的传输映射为：

- HTTP：请求 Header；
- gRPC：request Metadata；
- Dubbo：request Attachment；
- Thrift：Apache Thrift 官方 HTTP Transport 请求 Header。

## TrafficContext

`TrafficContext` 统一承载可选的 `campaign`、`lane`、`bucket` 标签。使用
`TrafficContext.attach(context)` 安装并在 `Scope.close()` 时恢复上一个上下文；
`TrafficContext.current()` 在未安装时返回空值。`TargetServiceMetadata.encode` 可接收显式
`TrafficContext`，显式值优先于 current，并在同一出站装配点更新 `baggage`：旧的
`latticehub.traffic.*` 成员被覆盖，外部成员继续下游传播；无结果时删除 Header。

`opentelemetry-api` 是 Maven optional 依赖。运行时存在该类库时可调用
`TrafficContext.tryInstallOpenTelemetryBridge()`，同时把领域上下文及 version、campaign、lane、
bucket 写入 OTel Context/Baggage，标准 W3C Baggage Propagator 可直接发送；领域值缺失时会从
合法 OTel Baggage 恢复。缺失 OTel 时核心类仍可加载并使用原生 `ThreadLocal` storage。
`TrafficContext.wrap(Runnable)`、`wrap(Callable)` 和 `wrap(Executor)` 可显式捕获并在 executor/
`CompletableFuture` 任务中恢复上下文，任务结束后恢复工作线程原值。此版本不安装 Spring、Dubbo、
gRPC 或 HTTP 框架自动 hook，也不负责重复 inject。

TrafficContext 构造与 Baggage 编解码失败时抛出 `TrafficContextException`。该异常继续继承
`IllegalArgumentException` 以保持现有 catch 兼容，并通过 `getCode()` 返回稳定的 conformance
机器码；`getDiagnostic()` 返回对应的 `TrafficContextDiagnostic` 枚举。

## Spring Boot Adapter

显式依赖按 Spring Boot 主版本选择且只能选择一个：

```xml
<dependency>
    <groupId>io.github.lattice-hub</groupId>
    <artifactId>pole-spring-cloud-boot-3</artifactId>
    <version>${pole.version}</version>
</dependency>
```

当前支持矩阵为 Boot `2.7.18`、`3.5.16`、`4.1.0`，统一要求 JDK 17。三代模块注册同一套
Spring Cloud LoadBalancer request transformer：保留原 path/query，把目的地址改写为 Sidecar
HTTP listener，并写入 target service Header 与 W3C Baggage。Servlet 入站 Filter 与 WebFlux
入站 WebFilter 提取 `TrafficContext`，请求结束后恢复原值；非法保留字段返回 HTTP 400。
`TaskDecorator`、Boot task executor customizer 与 Reactor scheduler hook 负责跨异步边界传播。
namespace 依次读取 `pole.namespace`、`POD_NAMESPACE`，最后使用 `default`。

## Java Agent

`pole-java-agent` 对外仍是单一可执行 Agent JAR，内部按 `API -> core -> plugins -> distribution`
分层。core 不引用 Spring、Dubbo、gRPC 或 Thrift；内置插件通过 `ServiceLoader` 发现，并只负责
框架探测、instrumentation 与对应 adapter payload 的选择。当前已实现 Spring Cloud 插件，Dubbo、
业务 gRPC 与 Thrift 插件保留为独立后续模块，不在 core 中预埋协议逻辑。

```shell
java -javaagent:/opt/pole/java-agent/pole-java-agent.jar -jar application.jar
```

Agent 在 `SpringApplication` 加载时识别 Boot 主版本并安装对应 initializer。它不做逐请求字节码
增强；请求改写、TrafficContext 与 Sidecar 连接仍由和显式依赖完全相同的 adapter 实现。Agent
必须在 JVM 启动时通过 `-javaagent` 提供，不支持应用启动后的动态 attach。

当前尚未实现 Dubbo、Thrift client 或业务 gRPC adapter。

Agent 也可构建为只携带 JAR 的多架构 artifact image：

```shell
docker buildx build --platform linux/amd64,linux/arm64 \
  -f agent/docker/Dockerfile -t pole-java-agent:local .
```

镜像固定提供 `/opt/pole/java-agent/pole-java-agent.jar`，适合由 Kubernetes init container
复制到业务容器共享卷；它不是应用运行时镜像，不负责启动业务 JVM。

## 契约

[`contract/`](contract/) vendoring 了当前 specification 工作树中的：

- `api/v1/sidecar/bootstrap.proto`；
- `thin-sdk/bootstrap/v1/README.md`；
- `thin-sdk/target-service/v1/README.md`、schema 和 conformance vectors。
- `thin-sdk/traffic-context/v1/README.md`、schema 和 conformance vectors。

来源状态记录在 [`contract/VERSION`](contract/VERSION)，校验和记录在
[`contract/SHA256SUMS`](contract/SHA256SUMS)。

## 验证

```shell
mvn clean verify
```

reactor 测试包含真实 gRPC over UDS 集成、首帧校验、断联失效、重连恢复、初始化超时、
TargetService 输入校验、canonical 编码一致性，以及 Spring Boot 2/3/4 自动配置验证。
