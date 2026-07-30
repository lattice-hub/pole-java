# Pole Java Thin SDK

`pole-client-java` 是 Java 17 的框架无关 Thin SDK 核心包，负责实现
`TargetEnvelope v1` 的构造、校验和 HTTP Header 编码。

## 状态

当前工程实现 specification tag
[`thin-sdk-contract-v1.0.0`](https://github.com/lattice-hub/specification/tree/thin-sdk-contract-v1.0.0/thin-sdk/target-envelope/v1)，
vendored 资产与来源 commit 记录在 [`contract/`](contract/)。
Pole Sidecar 尚未实现对应的本地专用 listener，因此当前版本只提供契约核心，
不能宣称已经完成 Thin SDK 到 Sidecar 的端到端接入。

## 要求

- Java 17 或更高版本
- Maven 3.8 或更高版本
- 主产物没有运行时依赖

## 使用

```java
import io.pole.client.PoleClientDefaults;
import io.pole.client.TargetEnvelope;
import io.pole.client.TargetEnvelopeHttpHeaders;
import java.util.Map;

TargetEnvelope envelope = TargetEnvelope.builder()
        .namespace("default")
        .service("orders")
        .protocol("grpc")
        .method("CreateOrder")
        .originalEndpoint("orders.internal:8080")
        .build();

Map<String, String> headers = TargetEnvelopeHttpHeaders.encode(envelope);
String sidecarEndpoint = PoleClientDefaults.SIDECAR_ENDPOINT;
```

使用 `TargetEnvelopeHttpHeaders.encode(existingHeaders, envelope)` 可安全合并
调用方 Header。编码器会按大小写不敏感方式删除已有内部 Header，写入规范名称，
并以确定性顺序返回不可修改的 Map。

Header value 使用契约定义的 canonical UTF-8 `%HH` 编码，避免不同 Java HTTP
client 对 Unicode Header 的处理差异。

当前核心包不包含 Spring、Dubbo、HTTP client 或 RPC 框架 adapter。

## 验证

```shell
mvn clean verify
```
