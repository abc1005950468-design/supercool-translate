# 安全策略

## 禁止入库

- API Key、密码、Cookie、OAuth Token
- `local.properties`、签名证书和私钥
- 用户录音、照片、数据库和日志
- 模型缓存、APK 和构建产物

## 云端凭据

App 的 API Key 使用 Android `EncryptedSharedPreferences` 保存在本机。仓库中只允许出现占位符和测试假值。

## 漏洞报告

不要在公开 Issue 中粘贴凭据或个人信息。请通过仓库维护者提供的私有渠道报告，并附最小复现步骤。
