# 经验记录

- Java Thin SDK 的核心包保持零运行时依赖；框架 adapter 必须拆成独立模块。
- Unicode 控制字符按 General Category `Cc` 校验，不能只检查 ASCII 范围。
- Sidecar 尚未消费 TargetEnvelope 前，不能把核心包测试表述为端到端接入完成。
