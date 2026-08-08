# Java Agent

Agent 对外发布一个 `pole-java-agent` shaded JAR，对内按职责拆分：

```text
pole-agent-api
    -> pole-agent-core
    -> plugins/pole-agent-plugin-*
    -> pole-java-agent
```

- `pole-agent-api`：稳定且 framework-neutral 的 `PoleAgentPlugin`、`PoleAgentContext` SPI；
- `pole-agent-core`：`premain`、`ServiceLoader` 发现、插件顺序与重复 ID 校验、应用 ClassLoader
  级 payload 隔离；
- `plugins/pole-agent-plugin-spring-cloud`：探测 `SpringApplication`，选择 Boot 2、3、4 payload；
- `pole-java-agent`：唯一对外 Agent JAR，合并 SPI descriptor，不暴露内部模块组合；
- `smoke-tests`：检查 shaded 分发，并分别用 Boot 2、3、4 携带真实 `-javaagent` 启动。

core 不引用具体框架，也不实现逐 RPC 治理。插件只负责启动期探测、instrumentation 和 adapter
选择；请求改写、目标身份、`TrafficContext` 与 Sidecar 连接均由 `adapters/` 和 Thin SDK 实现。

后续 Dubbo、业务 gRPC、Thrift 支持应分别新增：

```text
agent/plugins/pole-agent-plugin-dubbo
agent/plugins/pole-agent-plugin-grpc
agent/plugins/pole-agent-plugin-thrift
```

每个插件拥有独立依赖、匹配器、payload package 和测试，不允许把框架版本判断放回 core。
这些插件当前尚未实现。

## JAR 使用

Agent 仅支持 JVM 启动期安装，不支持动态 attach：

```shell
java -javaagent:/opt/pole/java-agent/pole-java-agent.jar -jar application.jar
```

## Docker Artifact Image

构建镜像：

```shell
docker buildx build --platform linux/amd64,linux/arm64 \
  -f agent/docker/Dockerfile -t pole-java-agent:local .
```

最终镜像固定携带：

```text
/opt/pole/java-agent/pole-java-agent.jar
```

该镜像面向 init container / artifact carrier 场景，本身不启动 JVM。单平台本地验证：

```shell
agent/docker/verify-image.sh pole-java-agent:local linux/arm64
```
