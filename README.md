# Pole Java Thin SDK

`pole-client-java` 是 Java 17 的框架无关 Thin SDK 核心包。它负责：

- 通过 gRPC over Unix Domain Socket 与本地 Pole Sidecar 建立长期内部会话；
- 接收 Sidecar 首帧主动下发的 HTTP、gRPC、Dubbo、Thrift listener 端口；
- 向框架 adapter 提供线程安全的本地 listener 地址；
- 构造仅含 `namespace`、`service` 的不可变 `TargetService`；
- 生成业务协议使用的 canonical target service 元信息。

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
import io.pole.client.SidecarBootstrapClient;
import io.pole.client.SidecarProtocol;
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

Sidecar 必须把完整 listener 表作为 `OpenSession` 首帧发送。SDK 验证四种协议、端口
范围和协议唯一性后原子安装不可变快照。会话断开时快照立即失效，
`listenerAddress` 和 `listenerAddresses` 快速抛出 `SidecarUnavailableException`；
后台继续指数退避重连，新会话首帧通过后才恢复。

## Target Service

```java
import io.pole.client.TargetService;
import io.pole.client.TargetServiceMetadata;
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

当前核心包不包含 Spring、Dubbo、Thrift、HTTP client 或业务 gRPC adapter。

## 契约

[`contract/`](contract/) vendoring 了当前 specification 工作树中的：

- `api/v1/sidecar/bootstrap.proto`；
- `thin-sdk/bootstrap/v1/README.md`；
- `thin-sdk/target-service/v1/README.md`、schema 和 conformance vectors。

来源状态记录在 [`contract/VERSION`](contract/VERSION)，校验和记录在
[`contract/SHA256SUMS`](contract/SHA256SUMS)。

## 验证

```shell
mvn clean verify
```

测试包含真实 gRPC over UDS 集成、首帧校验、断联失效、重连恢复、初始化超时、
TargetService 输入校验及 canonical 编码一致性。
