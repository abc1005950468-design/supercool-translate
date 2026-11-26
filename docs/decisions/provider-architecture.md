# 引擎与模型 Provider 架构（2026-10-19）

## 目标

同一套 App 同时支持：

- 本地离线模型（sherpa ASR、Qwen GGUF、未来 TTS）。
- 云端模型 API（OpenAI 兼容）。
- 后期把云端地址换成自建服务器，不改 App 代码。

## 分层

```
LiveViewModel
  ├─ ASR: StreamingAsr（本地）
  │        └─ 云端识别适配器（M2）
  └─ MT: TranslationEngine
           ├─ FastTranslator（ML Kit，默认）
           ├─ CloudTranslator（OpenAI 兼容）
           └─ LocalLlmTranslator（M7，llama.cpp + Qwen GGUF）
```

`TranslationEngine` 统一接口：

```kotlin
interface TranslationEngine {
    val id: String
    suspend fun prepare(source: String, target: String): Result<Unit>
    suspend fun translate(text: String, source: String, target: String): String
    fun close()
}
```

`TranslationEngines.create(config)` 根据配置选择引擎；云端失败自动回退 ML Kit。

## 模型目录

`ModelCatalog` 统一描述 ASR / LLM / TTS：

- `ru-small`：29 MB，俄语流式。
- `zh-en`：189 MB，中英流式。
- `qwen3-1.7b-q4km`：约 1.1 GB，精译默认。
- `qwen2.5-3b-q4km`：约 2.1 GB，质量模式。

每个包带 repo、文件、字节数、许可证、最低内存。`ModelRepository` 对任意 `ModelPack` 做断点续传、大小校验、导入、删除。

## 云端配置

- 协议：OpenAI 兼容 `/v1/chat/completions`。
- Base URL 支持 `https://api.deepseek.com/v1`、DashScope 兼容模式、vLLM、Ollama、llama.cpp server。
- API Key 存在 `EncryptedSharedPreferences`（AES-256-GCM）；失败时回退普通 prefs。
- PC 测试用环境变量或 `.env`，不写入仓库。

## PC 模拟

- `work/venv-sherpa` + `work/harness` 已能跑 sherpa 识别。
- `test_cloud_translate.py` 用同一个 OpenAI 兼容协议测试云端翻译。
- 后续用 Android 模拟器跑 APK；模型可通过应用内下载或 `adb` 导入。

## 服务器路径

自建 vLLM / llama.cpp server 暴露 `/v1/chat/completions` 后，App 只需把 Base URL 指向服务器；模型和提示词不变。

## 当前状态

- 已实现：ML Kit、CloudTranslator、ProviderStore、模型目录、模型下载、设置面板。
- 待实现：云端 ASR（M2）、本地 llama.cpp（M7）、TTS provider。
