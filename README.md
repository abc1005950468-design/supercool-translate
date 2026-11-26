# 超酷翻译 / SuperCool Translate

中俄双向 Android 翻译应用，面向无 Google 服务、弱网和无网场景。

当前源码处于 `3.5` 共轨开发阶段，重点解决本地 NLLB 在部分手机上的内存峰值与进程终止问题。

## 主要能力

- 中俄双向实时语音翻译
- 本地中俄 OCR 拍照翻译
- 文本翻译
- 本地 NLLB、ML Kit、OpenAI 兼容云端 API
- 本地历史记录、拍照任务恢复、模型管理
- 中俄物流场景术语与上下文设置

## 仓库结构

- `android/`：Android App、JNI/CMake 与单元测试
- `docs/`：架构、当前状态、已知问题和历史方案
- `tasks/`：Agent 任务卡模板
- `scripts/`：构建依赖恢复脚本
- `work/`：本地校准与临时产物，不入库

## 新机器恢复

1. 配置 JDK 17 和 Android SDK。
2. 恢复 AAR、ONNX Runtime 和 OCR 语言包：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/fetch-dependencies.ps1
```

3. 下载内置语音模型：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/fetch-bundled-models.ps1
```

4. 构建：

```powershell
gradle -p android testDebugUnitTest assembleDebug
```

主要 APK 输出位于 `android/app/build/outputs/apk/debug/app-debug.apk`。

## 当前优先事项

1. 修复 Nubia NX769J 上本地 NLLB 单句翻译触发系统 `SIGKILL` 的问题。
2. 验证 API Key 自动保存、切换离线和重启后的持久化。
3. 完成连续中文语音长时间稳定性测试。
4. 发布可重复构建的 arm64 侧载 APK。

详细状态见 [docs/PROJECT_STATE.md](docs/PROJECT_STATE.md)，协作规则见 [AGENTS.md](AGENTS.md)。

`android/app/build/outputs/apk/debug/app-debug.apk`。

## 当前优先事项

1. 修复 Nubia NX769J 上本地 NLLB 单句翻译触发系统 `SIGKILL` 的问题。
2. 验证 API Key 自动保存、切换离线和重启后的持久化。
3. 完成连续中文语音长时间稳定性测试。
4. 发布可重复构建的 arm64 侧载 APK。

详细状态见 [docs/PROJECT_STATE.md](docs/PROJECT_STATE.md)，协作规则见 [AGENTS.md](AGENTS.md)。

