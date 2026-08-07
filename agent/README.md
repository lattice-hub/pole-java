# Java Agent

该目录承载薄 Java Agent 的 bootstrap、插件加载和按框架版本拆分的安装模块。Agent
只负责启动期版本识别、classloader 隔离、插件选择及自动装配 `adapters/` 中的实现，
不通过逐 RPC 字节码增强复制治理行为。

`pole-java-agent` 构建为单一 shaded JAR，内含 Thin SDK、共享 Spring adapter、Boot 2/3/4
安装模块和 Byte Buddy。通过 `-javaagent` 在 `SpringApplication` 定义时安装对应 initializer：

```shell
java -javaagent:/opt/pole/pole-java-agent.jar -jar application.jar
```

Agent 只支持 JVM 启动期安装，不支持动态 attach；不在运行期拦截每个 RPC。
