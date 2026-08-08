# Agent Plugins

每个协议插件族部署为 `plugins/<plugin-family>/`，由 core 为整个插件族创建独立 ClassLoader。
插件族通过 `META-INF/services/io.github.latticehub.agent.api.PoleAgentPlugin` 注册入口；需要多个
版本 JAR 时使用协议自己的 provider SPI，避免重复安装 instrumentation。

插件职责仅限：

1. 声明稳定且唯一的插件 ID；
2. 在 `premain` 阶段安装框架探测或 instrumentation；
3. 按应用版本选择自己的 adapter payload package；
4. 使用 `PoleAgentContext` 以应用 ClassLoader 为 parent 隔离加载 payload；
5. 通过 bootstrap bridge 暴露增强后应用类型需要触发的通用事件，不能要求应用 ClassLoader
   直接看到插件实现类。

插件不得复制 Thin SDK、目标身份、流量上下文或逐 RPC 治理逻辑，也不得把框架依赖泄漏到
`pole-agent-core`。当前 `spring-cloud-plugins/` 包含单一入口以及 `spring-cloud-3x-plugin`、
`spring-cloud-4x-plugin`、`spring-cloud-5x-plugin`；入口按 Boot major 初筛，并在 Cloud 版本可见时
校验 Cloud major。其他内置插件族为：

- `dubbo-plugins/dubbo-3x-plugin`：增强 Dubbo 3.x consumer/provider 内建 Filter，通过 invocation
  attachment 传播 W3C Baggage；
- `grpc-plugins/grpc-1x-plugin`：在 channel/server builder 构建前安装 gRPC 1.x client/server
  interceptor，并在服务端 listener callback 恢复 `TrafficContext`；
- `thrift-plugins/thrift-http-plugin`：增强 `THttpClient` 与 `TServlet`，仅承诺 Thrift-over-HTTP。

Thrift Framed + Binary/Compact/Multiplexed 原生 TCP 传输没有通用 metadata carrier，插件不会修改
帧格式或虚构透明传播能力。
