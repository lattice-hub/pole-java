# 经验记录

- Java Thin SDK 的核心包保持零运行时依赖；框架 adapter 必须拆成独立模块。
- Unicode 控制字符按 General Category `Cc` 校验，不能只检查 ASCII 范围。
- Sidecar 尚未消费 TargetEnvelope 前，不能把核心包测试表述为端到端接入完成。
- Thin SDK 不负责逐请求向 Sidecar 查询真实目标实例；它通过长期 gRPC over UDS
  会话接收 Sidecar 首帧推送的本地协议 listener 端口，业务请求始终访问本地
  listener，并携带仅含 namespace/service 的 target service 元信息。
- listener 端口不能内置在 SDK；UDS 断联后必须立即使快照失效，防止继续使用
  已被其他进程占用的旧端口。
