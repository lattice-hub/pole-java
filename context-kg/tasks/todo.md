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
