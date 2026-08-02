# Java Thin SDK

- [x] 核对 Thin SDK 契约与一致性向量
- [x] 创建 Maven/Java 17 工程
- [x] 实现不可变 `TargetEnvelope`
- [x] 实现校验与确定性 Header 编码
- [x] 补充测试、README 和许可证
- [x] 运行编译与 JUnit 验证

## Maven Central 发布准备与一致性修复

- [x] 检查现有 POM、endpoint 校验与测试
- [x] 固定编译插件并补齐 Central 元数据
- [x] 配置 sources 与 javadoc 产物
- [x] 补充非法 host 字符测试
- [x] 运行测试、打包与依赖验证

## Review

- 主产物零运行时依赖，JUnit 只用于测试。
- 34 个 JUnit 测试通过，覆盖 Unicode 空白、Unicode `Cc`、endpoint 边界、
  Header 顺序和内部 Header 覆盖。
- Maven 3.8.8 与 3.9.11、JDK 17 下 `clean verify` 均通过。
- 主 JAR、sources JAR 和 javadoc JAR 均成功生成；javadoc 仅报告缺少注释警告。
- `dependency:tree -Dscope=runtime` 仅包含工程自身，保持零运行时依赖。
- 版本保留为 `0.1.0-SNAPSHOT`，未执行发布。
- 当前只实现契约核心，不包含 Spring、Dubbo 或 HTTP client adapter。
- Sidecar 本地 listener 尚未实现，因此没有端到端接入证据。

## 2026-07-31 正式 Thin SDK 契约接入

- [x] vendoring `thin-sdk-contract-v1.0.0` 契约资产与来源信息
- [x] 对齐 Unicode scalar、精确 White_Space 与 endpoint 规则
- [x] 实现 canonical UTF-8 Header 编码
- [x] 执行全部 SDK 与语言特定一致性向量
- [x] 校验 Sidecar receive 资产结构完整性
- [x] 更新 lattice-hub URL、README、CI 与治理文件
- [x] 运行 Java 17 本地验证并配置 Java 17/21 CI

## 2026-07-31 正式契约接入 Review

- vendored checksum 与 specification tag `thin-sdk-contract-v1.0.0` 一致。
- JDK 17、Maven 3.9.16 下 `mvn -B clean verify` 通过，40 个测试全绿。
- CI 矩阵覆盖 Temurin Java 17/21；正式包仍为 `0.1.0-SNAPSHOT`，未发布。
- 最终审查修复非 ASCII IPv4 差分、checksum/VERSION 假阳性，并为手动发布检查
  增加 SNAPSHOT gate。

## 2026-08-02 Sidecar Bootstrap v2 改造

- [x] 检查仓库约束、工作区状态与旧 API 影响范围
- [x] 核对 specification bootstrap proto 与 gRPC Java UDS 官方接入方式
- [x] vendoring `bootstrap.proto` 并配置 protobuf/gRPC 代码生成
- [x] 用不可变 `TargetService` 替换旧 `TargetEnvelope`
- [x] 实现 UDS `OpenSession`、首帧校验与原子 listener 快照
- [x] 实现断流失效、指数退避重连与有界初始化超时
- [x] 暴露线程安全的协议 listener 地址读取 API
- [x] 更新契约资产、README 与完整测试
- [x] 运行针对性测试和完整 Maven 验证

## 2026-08-02 Sidecar Bootstrap v2 Review

- 删除旧 `TargetEnvelope` 与固定 Sidecar HTTP endpoint，公共目标模型收敛为仅含
  `namespace/service` 的不可变 `TargetService`。
- `TargetServiceMetadata` 只写入 `latticehub-target-namespace/service`，保留
  Unicode scalar、`Cc`、White_Space 与 canonical UTF-8 `%HH` 规则。
- `SidecarBootstrapClient` 通过 Java 17 `UnixDomainSocketAddress`、gRPC Java
  `grpc-netty-shaded` NIO domain socket channel 建立 `OpenSession` 长连接，不依赖
  Epoll/KQueue 平台 native classifier。
- 首帧必须完整包含 HTTP、gRPC、Dubbo、Thrift 四种 listener；快照通过
  `AtomicReference` 原子安装，断流或关闭时立即失效，后台有界指数退避重连。
- SDK 主代码未出现 `15001..15004`，listener 地址只来自 Sidecar 首帧。
- vendored proto、README、schema 和 conformance 原先与 specification
  `v0.1.0-ALPHA.39` 一致；Java namespace 迁移后已固定到尚未发布的 `develop`
  提交 `776f590d1474c51847af75b44522953874097e55`。
- Temurin JDK 17.0.19、Maven 3.9.16 下针对性测试和 `mvn -B clean verify`
  均通过，共 20 个测试；包含真实 macOS ARM64 UDS gRPC 集成测试。
- `mvn dependency:tree -Dscope=runtime` 确认新增 gRPC/Protobuf 运行时依赖；核心包不再
  是零运行时依赖，这与 UDS bootstrap 职责一致。

## 2026-08-02 Java Namespace 迁移

- [x] Maven `groupId` 调整为已验证 namespace `io.github.lattice-hub`。
- [x] Thin SDK 公共 package 调整为 `io.github.latticehub.client`。
- [x] Specification 生成类型引用迁移到新的 Java package。
- [x] 完成 Maven 全量验证与跨仓契约校验。

### Review

- Maven namespace 与 Java package 分离：前者允许连字符，后者使用合法且稳定的 `io.github.latticehub.client`，不重复加入 `pole` 层级。
- Temurin 17 与 Maven 3.9.16 下 `mvn -B clean verify` 通过，21 个测试中 20 个通过、1 个显式跳过的 live Sidecar 测试。
