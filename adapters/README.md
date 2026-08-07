# Framework Adapters

该目录承载显式 SDK 与 Java Agent 共同复用的 framework adapter。adapter 是请求级
目标身份和 `TrafficContext` extract/inject 的唯一行为实现；Java Agent 不复制这些逻辑。

当前模块：

- `pole-spring-common`：共享 Spring Cloud LoadBalancer blocking/reactive 出站 transformer；
- `pole-spring-boot-2`：Boot 2.7、Spring 5、`javax.servlet` 自动配置；
- `pole-spring-boot-3`：Boot 3.5、Spring 6、`jakarta.servlet` 自动配置；
- `pole-spring-boot-4`：Boot 4.1、Spring 7、`jakarta.servlet` 自动配置。

三代统一以 JDK 17 为最低要求。显式接入时只引入与应用 Boot 主版本对应的一个模块。
