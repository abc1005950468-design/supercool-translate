# 架构概览

## Android

- Kotlin + Jetpack Compose，单 Activity 主导航
- `LiveViewModel` 统一管理同传、记录、模型、拍照任务和翻译引擎
- `RecordStore` 保存文字记录；拍照记录仅保存文字
- `PhotoTaskStore` 保存当前拍照任务和临时图片，重启可恢复
- `ProviderStore` 使用 EncryptedSharedPreferences 保存引擎与云端凭据

## ASR

- 流式中俄轻量识别
- 中文 Paraformer、俄语 GigaAM/Whisper 句末校正
- Whisper LID 音频级语种识别

## 翻译

- NLLB-200 600M/1.3B：ONNX Runtime + JNI，专用翻译
- ML Kit：可选，需要 Google 服务
- CloudTranslator：OpenAI 兼容 `/chat/completions`
- Gemma/LiteRT-LM：实验路径

## OCR

- Tesseract4Android
- 内置 `chi_sim` 与 `rus` traineddata
- 独立中文/俄文识别后按脚本覆盖率与置信度选择

## 关键内存边界

- NLLB 使用 Native 内存，不受 Android Java heap/largeHeap 限制
- 厂商可能限制单进程总内存
- 内存相关改动必须做真机 PSS/退出原因验证
