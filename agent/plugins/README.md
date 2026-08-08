# Agent Plugins

每个 Agent 插件是独立 Maven module，并通过
`META-INF/services/io.github.latticehub.agent.api.PoleAgentPlugin` 注册。

插件职责仅限：

1. 声明稳定且唯一的插件 ID；
2. 在 `premain` 阶段安装框架探测或 instrumentation；
3. 按应用版本选择自己的 adapter payload package；
4. 使用 `PoleAgentContext` 以应用 ClassLoader 为 parent 隔离加载 payload。

插件不得复制 Thin SDK、目标身份、流量上下文或逐 RPC 治理逻辑，也不得把框架依赖泄漏到
`pole-agent-core`。当前仅有 `pole-agent-plugin-spring-cloud`；Dubbo、业务 gRPC、Thrift 插件尚未实现。
