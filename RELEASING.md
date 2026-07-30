# 发布流程

`发布检查` workflow 只构建候选 JAR 并上传 Actions artifact，不发布到 Maven
Central。正式发布前必须确认版本、契约 tag、Central namespace、签名和凭据，
再通过独立审核的发布变更启用上传。
