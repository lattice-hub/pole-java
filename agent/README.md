# Java Agent

Agent 发布为目录化运行时，对内按职责拆分：

```text
pole-java-agent (thin premain JAR)
    -> pole-agent-bootstrap (bootstrap-visible bridge)
    -> lib/pole-agent-api + pole-agent-core
    -> plugins/<plugin-family>/*.jar
```

- `pole-agent-bootstrap`：由 bootstrap ClassLoader 加载的通用事件桥，使增强后的应用类型无需
  看到插件 ClassLoader；
- `pole-agent-api`：稳定且 framework-neutral 的 `PoleAgentPlugin`、`PoleAgentContext` SPI；
- `pole-agent-core`：插件目录扫描、每插件族 ClassLoader、`ServiceLoader` 发现、顺序与重复 ID
  校验、应用 ClassLoader 级 payload 隔离；
- `plugins/spring-cloud-plugins`：单一入口探测 `SpringApplication`，动态选择 Cloud 3x/4x/5x provider；
- `plugins/dubbo-plugins`：Dubbo 3.x invocation attachment 传播；
- `plugins/grpc-plugins`：gRPC 1.x client/server interceptor 自动安装；
- `plugins/thrift-plugins`：Apache Thrift-over-HTTP client/server header 传播；
- `pole-java-agent`：薄 `premain` JAR，并组装 `lib/`、`plugins/` 与 distribution ZIP；
- `smoke-tests`：检查目录边界，并用 Boot 2/3/4、Dubbo 3.3、gRPC 1.x、Thrift HTTP 携带真实
  `-javaagent` 执行框架调用。

core 不引用具体框架，也不实现逐 RPC 治理。插件只负责启动期探测、instrumentation 和 adapter
选择；请求改写、目标身份、`TrafficContext` 与 Sidecar 连接均由 `adapters/` 和 Thin SDK 实现。

每个插件拥有独立依赖、匹配器、payload package 和测试，不允许把框架版本判断放回 core。
Thrift 插件只支持能承载 HTTP Header 的 `THttpClient`/`TServlet`；原生 Framed/Binary/Compact
传输保持协议兼容，但不承诺无侵入 TrafficContext 传播。

## 分发与使用

必须完整保留以下相对目录，不能只复制入口 JAR：

```text
pole-java-agent/
├── pole-java-agent.jar
├── lib/
└── plugins/
    ├── spring-cloud-plugins/
    ├── dubbo-plugins/
    ├── grpc-plugins/
    └── thrift-plugins/
```

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
/opt/pole/java-agent/lib/
/opt/pole/java-agent/plugins/
```

该镜像面向 init container / artifact carrier 场景，本身不启动 JVM。单平台本地验证：

```shell
agent/docker/verify-image.sh pole-java-agent:local linux/arm64
```
