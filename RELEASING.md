# 发布流程

`发布检查` workflow 只构建候选 JAR 并上传 Actions artifact，不发布到 Maven
Central。

## Snapshot

保持 `pom.xml` 版本为 `*-SNAPSHOT`，在 Actions 手动运行
`发布 Snapshot 到 Maven Central`（`workflow_dispatch`）。不会创建 GitHub
Release，也不会改写版本号。

消费 Snapshot 需额外加入 Central Portal Snapshots 仓库：

`https://central.sonatype.com/repository/maven-snapshots/`

## Release

通过 GitHub Release（tag `vX.Y.Z`）触发 `发布到 Maven Central`。正式发布前
必须确认版本、契约 tag、Central namespace、签名和凭据。
