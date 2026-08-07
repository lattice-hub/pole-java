# 贡献指南

1. 从 `develop` 创建短生命周期分支。
2. 保持 `pole-client-java` 无 Spring、Dubbo 等框架依赖；framework adapter 只能加入 `adapters/`
   下的独立模块，Agent 只能自动装配这些 adapter，不能复制请求级治理逻辑。
3. 契约行为必须先在 `lattice-hub/specification` 发布，再同步 vendored 资产。
4. 提交前在 reactor 根目录运行 `mvn --batch-mode clean verify`，并分别在
   `contract/`、`contract/traffic-context/v1/` 执行
   `shasum -a 256 -c SHA256SUMS`。
5. Pull Request 需说明行为变化、契约版本和验证结果。
