# Java Thin SDK

## 2026-08-07 pole-java Monorepo 重组

- [x] 核对当前单模块依赖与未提交改动
- [x] 将现有核心迁入 `pole-client-java` reactor 模块并保持单 JAR
- [x] 建立 adapters 与 agent 聚合边界
- [x] 同步仓库命名、文档和发布流水线
- [x] 运行模块定向测试与全量验证
- [x] 完成代码复核并记录 Review

### Review

- GitHub 仓库已从 `lattice-hub/pole-client-java` 改名为 `lattice-hub/pole-java`，
  本地目录和 `origin` 同步更新；根 POM 改为 `pole-java-parent` reactor。
- 现有源码、proto 与测试迁入 `pole-client-java/` 模块，Maven 坐标和
  `io.github.latticehub.client` package 保持不变。复核阶段放弃 API/Sidecar 双 JAR
  方案，避免 JPMS/module-path split-package。
- 新增 `pole-java-bom`、`adapters/` 与 `agent/` 聚合边界。adapter 是唯一请求级
  行为实现，Agent 仅负责启动期识别、隔离、插件选择和自动装配；空边界不宣称已有实现。
- CI 同时校验 bootstrap、target-service 与 TrafficContext 两组 SHA256，发布检查上传
  reactor JAR，release workflow 对全部模块统一改版本。
- `maven:3.9.11-eclipse-temurin-17` 下 `mvn clean verify` 与
  `mvn -Prelease -Dgpg.skip=true clean verify` 均通过：38 项测试、0 失败、1 项 live
  Sidecar 测试跳过；两组契约校验和、runtime dependency tree、JAR 内容和
  `git diff --check` 均已验证。

## 2026-08-06 TrafficContext v1 传播

- [x] 核对现有 metadata 编码与 contract 资产路径
- [x] 先补 TrafficContext 编解码与 scope 测试
- [x] 实现 native storage 与可选 OTel bridge
- [x] 接入 TargetService metadata 编码与下游 baggage 保留
- [x] 同步 v1 contract 与 README
- [x] 完成最终 Maven 全量验证

### Review

- 已实现 `TrafficContext`、W3C Baggage codec、`ThreadLocal` scope 和反射式 optional
  OTel Context/Baggage bridge；标准 W3C Baggage Propagator 可发送四个保留成员，领域值缺失时
  会从合法 OTel Baggage 恢复；`TargetServiceMetadata` 在同一装配点注入 target 与 baggage。
- 已同步当前 `specification/thin-sdk/traffic-context/v1` 的四项资产，并补充 canonical、
  OWS、空外部值、非法 foreign member、version-only 与大小写前缀回归测试。
- 根会话冒烟复验修正空 `TrafficContext` 注入：只清理旧保留成员，不生成 version-only carrier。
- 已补 `empty_context_cleans_reserved_prefix` 回归、OTel Baggage inject/recover，以及
  `wrap(Runnable/Callable/Executor)` 在线程池和 `CompletableFuture` 中显式捕获、恢复和清理的测试。
- OTel attach 现在枚举并删除全部精确小写 `latticehub.traffic.*`，恢复时拒绝未知保留键；
  空捕获的 wrapper 也建立清空 scope，屏蔽并在结束后恢复执行线程残留的 native/OTel context。
- Java 原生测试逐项执行 vendored `valid`、`sidecar_receive.valid` 与
  `sidecar_receive.invalid`。
- 新增继承 `IllegalArgumentException` 的 `TrafficContextException` 与公开
  `TrafficContextDiagnostic` 枚举；构造和 Baggage 校验均返回稳定 `getCode()`，conformance
  测试直接精确比较机器码，不再根据异常消息推断。
- `maven:3.9.11-eclipse-temurin-17` 容器执行 `mvn -B test` 通过：37 项测试、0 失败、
  1 个需要真实 Sidecar 的测试跳过；`git diff --check` 与四项资产逐字比对通过。
- 最终根会话复跑 Maven 全量测试确认 37 项通过、1 个 live Sidecar 测试跳过；先前容器下载
  超时和 OTel Baggage 无序输出断言均已消除，不再保留“完整 Maven 未验证”的旧边界。

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

## 2026-08-03 Maven Central 自动发布

- [x] 配置 Central Portal、GPG 与 GitHub Release 工作流
- [x] 验证 release profile、版本 gate 与候选产物
- [x] 提交并推送发布配置

### Review

- Release 发布时从 `vX.Y.Z` 标签设置 Maven 版本，使用 `maven-gpg-plugin`
  签名，并由 Central Portal Maven Plugin 自动发布及等待 `published` 状态。
- Temurin 17.0.20、Maven 3.9.11 下 `mvn -Prelease -Dgpg.skip=true clean verify`
  通过，21 个测试中 20 个通过、1 个 live Sidecar 测试跳过；sources、javadoc
  与主 JAR 均生成成功。

## 2026-08-06 OpenControlSession 迁移

- [x] 核对 `OpenControlSession` 权威契约与旧会话边界
- [x] 更新 vendored proto 与 Java 生成代码
- [x] 实现注册 API、状态处理与重连重放
- [x] 更新 README 和单元/UDS 集成测试
- [x] 运行可用校验并提交本地变更

### Review

- 契约引用 specification `develop` 集成提交 `2642bc29c0a512f4da84ec4eb862b1e1ceee9833`。
- Java 主源码已按 Maven 缓存的 protobuf/gRPC 版本生成验证命令准备；本机缺少 Java
  Runtime，gRPC 代码生成插件无法启动，因而 Maven/JUnit 未执行。
- `protoc` descriptor 校验、vendored checksum 与 diff 空白检查通过。

## 2026-08-07 Spring Boot Adapters 与 Java Agent

- [x] 定义共享 Spring 请求适配行为与安装 SPI
- [x] 实现 Spring Boot 2.7 / 3.5 / 4.1 安装模块
- [x] 实现启动期自动装配的单一 Java Agent JAR
- [x] 增加三代 Spring Boot 运行时集成测试
- [x] 更新 BOM、README 与发布产物说明
- [x] 执行 JDK 17 全量构建和产物复核

### Review

- JDK 17 作为 parent compiler release 和全部模块的最低运行基线；版本矩阵固定为
  Boot 2.7.18 / Spring 5.3.31 / Cloud 3.1.8、Boot 3.5.16 / Spring 6.2.19 /
  Cloud 4.3.0、Boot 4.1.0 / Spring 7.0.8 / Cloud 5.0.2。
- `pole-spring-common` 统一实现 blocking/reactive Spring Cloud LoadBalancer 请求改写、
  target Header 与 W3C Baggage 注入、Servlet/WebFlux 入站提取、TaskDecorator 和 Reactor
  异步传播；三代 Boot 模块只承担各自自动配置与 `javax`/`jakarta` 适配。
- `pole-java-agent` 是 28,755,589 字节的单一 shaded JAR，仅在 JVM 启动期增强
  `SpringApplication` 构造，按 Implementation-Version 选择对应 initializer；adapter payload
  使用隔离 classloader 加载，Agent 不复制请求级治理逻辑，也不支持动态 attach。
- Temurin JDK 17、Maven 3.9.11 容器下 `mvn -B clean verify` 与
  `mvn -B -Prelease -Dgpg.skip=true verify` 均通过。reactor 共执行 47 项测试，0 失败，
  1 项需要真实 Sidecar 的 live 测试跳过。
- 三个不依赖 adapter 的独立 Spring Boot 应用均通过 `-javaagent` 黑盒启动并输出
  `POLE_AGENT_OK:boot2`、`POLE_AGENT_OK:boot3`、`POLE_AGENT_OK:boot4`。Agent manifest、
  payload、gRPC service provider、无自动配置资源泄漏和 `git diff --check` 均已复核。

## 2026-08-08 Adapter 与 Agent 插件化重构

- [x] 确认 Agent 插件分发与装载形态
- [x] 拆分通用 Agent SPI 与运行时核心
- [x] 将 Spring Cloud 安装迁入独立插件
- [x] 建立 Dubbo、gRPC、Thrift 插件边界
- [x] 按框架和版本重组 adapter artifact/package
- [x] 补充插件发现、隔离与兼容性测试
- [x] 执行 JDK 17 全量构建和黑盒验证

### Review

- Agent 内部拆为 `pole-agent-api`、`pole-agent-core`、独立 `pole-agent-plugin-*` 与
  `pole-java-agent` distribution；core 仅负责 SPI 发现、生命周期和应用 ClassLoader 级 payload
  隔离，不再引用 Spring 类型、版本表或 adapter package。
- Spring Cloud adapter 迁入独立 family，artifact/package 统一使用 `pole-spring-cloud-*` 与
  `io.github.latticehub.adapter.springcloud.*`；Spring Cloud 插件通过 `ServiceLoader` 注册并选择
  Boot 2、3、4 payload。Dubbo、业务 gRPC、Thrift 的独立插件边界已写入插件规范，但功能尚未实现。
- 分发测试复核 manifest、SPI descriptor、三代 payload 和 Spring 自动配置资源隔离；三个独立
  Spring Boot 应用均使用最终 shaded JAR 的 `-javaagent` 成功启动并获得 adapter bean。
- JDK 17 / Maven 3.9.11 容器下 `mvn clean verify` 与
  `mvn -Prelease -Dgpg.skip=true verify` 均通过，共 56 项测试、0 失败、1 项 live Sidecar 测试跳过。
- `agent/docker/Dockerfile` 成功构建 `linux/arm64` artifact image，验证固定路径
  `/opt/pole/java-agent/pole-java-agent.jar`；CI 同时覆盖 `linux/amd64` 与 `linux/arm64`。

## 2026-08-08 插件化重构交付

- [x] 提交 Agent 插件化与 adapter 重组改动
- [x] 合并到 `develop` 并推送远端
- [x] 核对本地与远端 `develop` 一致

### Review

- 功能分支基于最新 `origin/develop`，经完整验证后以 fast-forward 方式合入并推送；
  本地与远端 `develop` 最终指向同一提交。

## 2026-08-08 Agent 目录化分发与 ClassLoader 隔离

- [x] 对照 Polaris Agent Core 的 bootstrap、extension 与插件装载边界
- [x] 审计现有 shaded JAR 的模块泄漏与 ClassLoader 可见性
- [x] 将 Agent 入口收敛为极薄 bootstrap JAR
- [x] 将 core/runtime 与插件改为独立目录和 ClassLoader
- [x] 保持 Spring Boot 2、3、4 adapter payload 隔离
- [x] 更新 Docker artifact image 与分发文档
- [x] 执行 JDK 17 全量、黑盒和镜像验证

### 设计约束

- 参考 Polaris 的职责切分，但不引入其面向 Java 8/9 的双 ASM、module boot 和动态 attach
  复杂度；Pole 最低 JDK 17，只保留薄 bootstrap、独立 runtime、插件目录和 SPI 装载。
- `pole-java-agent.jar` 仅承担 `premain`、定位安装目录、注册 bootstrap bridge 和反射启动
  core；`lib/` 放稳定 API/core，`plugins/` 每个插件一个自包含 JAR，并使用独立 ClassLoader。
- Spring Cloud 插件继续只做启动期增强和 Boot 2/3/4 payload 选择，请求级行为仍唯一位于
  `adapters/spring-cloud`。

### Review

- Agent 分发改为 `pole-java-agent.jar + lib/ + plugins/`，入口 JAR 不再携带 core、插件或
  adapter 实现；core 为每个插件建立独立 child-first ClassLoader，并通过 bootstrap bridge
  跨越增强代码与插件运行时边界。
- Spring Cloud 插件将 SDK、gRPC 等 payload relocation 到插件私有命名空间，只向应用
  ClassLoader 注入当前 Boot 版本及实际存在的 Web 技术栈 adapter，Boot 2、3、4 黑盒启动均通过。
- JDK 17 容器内 `clean verify` 和 release profile 均通过；ARM64 Docker artifact image 构建、
  校验脚本及 `/opt/pole/java-agent` 目录内容检查通过。
