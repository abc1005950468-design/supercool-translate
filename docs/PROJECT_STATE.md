# 项目状态

更新时间：2026-11-26

## 当前阶段

`3.5` 共轨开发版。源码可构建，但不是全机型稳定版。

## 已完成能力

- Kotlin + Jetpack Compose Android App
- 中俄双向实时语音翻译
- Tesseract 中俄离线 OCR 拍照翻译
- 文本翻译、本地记录和拍照任务恢复
- 本地 NLLB 600M/1.3B、ML Kit、OpenAI 兼容云端 API
- 模型下载、取消、续传和本地导入
- API Key 输入即写入加密存储，切回离线不覆盖
- 连续识别达到 120 字自动切段；本地 NLLB 不再翻译中间临时结果
- 停止同传后 20 秒空闲释放 NLLB、ASR、LID 和声纹模型

## 当前阻断问题

Nubia NX769J（Android 14）上，NLLB 600M 单句翻译的 Native 峰值可能超过约 4 GB，被系统以 `SIGKILL` 终止。Java Heap 不是原因，问题位于 NLLB/ONNX Runtime 的 Native 分配。

已在 `3.5` 尝试：

- 取消启动预加载
- 单线程、关闭 memory pattern、不同 arena 组合
- 停止后释放会话
- 中间识别结果不进入 NLLB

目前尚未证明 NLLB 600M 能在该机型约束下稳定运行。下一阶段应评估更小的中俄专用离线翻译模型或将实时翻译切到稳定的云端/可选引擎。

## 构建

```powershell
powershell -ExecutionPolicy Bypass -File scripts/fetch-bundled-models.ps1
gradle -p android testDebugUnitTest
gradle -p android assembleDebug
```

## 最新验证

- Kotlin 单元测试：15 项通过
- APK：217.18 MB，versionCode 16
- Nubia 真机：短句 NLLB 仍可能触发系统强杀
- API Key 自动保存代码已加入，但完整真机回归待补
