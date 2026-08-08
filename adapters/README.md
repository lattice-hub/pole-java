# Framework Adapters

该目录按 framework family 拆分 adapter。adapter 是请求级目标身份和 `TrafficContext`
extract/inject 的唯一行为实现；显式 SDK 接入与 Java Agent 复用同一实现。

当前 Spring Cloud family：

```text
spring-cloud/
  pole-spring-cloud-common
  pole-spring-cloud-boot-2
  pole-spring-cloud-boot-3
  pole-spring-cloud-boot-4
```

- `pole-spring-cloud-common`：Spring Cloud LoadBalancer blocking/reactive 出站 transformer；
- `pole-spring-cloud-boot-2`：Boot 2.7、Spring 5、`javax.servlet` 自动配置；
- `pole-spring-cloud-boot-3`：Boot 3.5、Spring 6、`jakarta.servlet` 自动配置；
- `pole-spring-cloud-boot-4`：Boot 4.1、Spring 7、`jakarta.servlet` 自动配置。

三代统一以 JDK 17 为最低要求。显式接入时只引入与应用 Boot 主版本对应的一个模块。
未来 Dubbo、业务 gRPC、Thrift adapter 应各自新增顶层 family，不与 Spring Cloud 共用 package。
