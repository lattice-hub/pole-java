# 发布流程

`发布检查` workflow 构建整个 `pole-java` reactor，并上传各模块候选 JAR，不发布到
Maven Central。

先在 [Central Publisher Portal](https://central.sonatype.com/) 对命名空间
`io.github.lattice-hub` 执行 **Enable SNAPSHOTs**（Namespaces → 下拉菜单），
否则 Snapshot 上传会返回 403。

## 版本与 tag 规则

通过 GitHub Release 触发 `发布到 Maven Central`。Maven 坐标由 tag 推导：

| Release tag | 发布到 Maven 的 version | 目标仓库 |
| --- | --- | --- |
| `v0.1.0` | `0.1.0`（与 tag 对齐） | Maven Central（release） |
| `v0.1.0-ALPHA.46` | `0.1.0-SNAPSHOT`（ALPHA → SNAPSHOT） | Central Portal Snapshots |
| `v0.1.0-RC.1` | `0.1.0-RC.1`（与 tag 对齐，非 ALPHA） | Maven Central（release） |

规则：

1. 去掉前缀 `v` 得到版本候选。
2. 若候选版本包含 `ALPHA`（大小写不敏感），取 `X.Y.Z` 并追加 `-SNAPSHOT`。
3. 否则 Maven version 与 tag（去 `v`）完全一致。

消费 Snapshot 需额外加入：

`https://central.sonatype.com/repository/maven-snapshots/`

## 手动 Snapshot（可选）

也可在不创建 Release 时，保持 `pom.xml` 为 `*-SNAPSHOT`，手动运行
`发布 Snapshot 到 Maven Central`（`workflow_dispatch`）。

## Release 前检查

正式非 ALPHA 发布前确认契约 tag、Central namespace、签名和凭据。

所有可发布模块使用统一版本。`pole-client-java` 保持 Thin SDK 核心入口，Spring Boot 2/3/4
adapter 与 `pole-java-agent` 均由 `pole-java-bom` 管理版本；`adapters/` 仅作为聚合 POM，
`pole-java-agent` 发布为无外部运行时依赖的 shaded JAR。
