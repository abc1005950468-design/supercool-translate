# 开源方案检索与选型（2026-10-19）

检索方式：GitHub REST API（本机未安装 `gh` / `agent-reach` CLI）。

## 结论

保留现有 Kotlin + Compose 工程，不直接 fork RTranslator。原因：

- RTranslator 3.0 是 Java + 大量 CMake 原生子模块（bergamot / sentencepiece / fastText / protobuf），当前压缩包不含子模块，构建成本高。
- 我们已有可用的 sherpa-onnx、ML Kit、云端 OpenAI 兼容层和记录/模型管理。
- RTranslator 作为**模式与音频交互的参考实现**，其 Apache-2.0 代码可按模块借鉴并保留 NOTICE。

## 选型表

| 能力 | 候选 | 许可证 | 决策 |
| --- | --- | --- | --- |
| 流式 ASR | `k2-fsa/sherpa-onnx`（15.4k） | Apache-2.0 | **已采用** |
| 实时翻译参考 App | `niedev/RTranslator`（10.5k） | Apache-2.0 | **参考**（Conversation / WalkieTalkie / Text） |
| Whisper Android | `vilassn/whisper_android`（694） | MIT | 备选；当前用 sherpa Whisper |
| 端侧翻译 | Bergamot / Firefox models（RTranslator、`DavidVentura/offline-translator`） | MPL-2.0 / GPL-3.0 | 后续评估；当前 ML Kit + Qwen |
| TTS | sherpa-onnx TTS、`HayaiApp/HayaiTTS` | Apache-2.0 / NOASSERTION | 先用系统 TTS，后续接 sherpa |
| 说话人分离 | sherpa-onnx diarization、`qutschwalze/meeting-transcriber-sherpa` | Apache-2.0 / 未标注 | P1 接入 sherpa |
| 说话人嵌入（已用） | `csukuangfj/speaker-embedding-models`（WeSpeaker resnet34 LM） | Apache-2.0 | 已接入在线聚类 |
| 会议助手参考 | `Helldez/HearoPilot-App` | Apache-2.0 | 参考纪要/摘要交互 |
| 屏幕/悬浮翻译 | `ciddwd/overlay-translator` | Apache-2.0 | 暂不需要 |

## 复用原则

1. 优先复用库（sherpa-onnx AAR），不复制 App 源码。
2. 需要借鉴 RTranslator 的逻辑时，保留 Apache-2.0 NOTICE 与文件头。
3. 不引入 GPL-3.0 代码到闭源分发版本。
4. 原生 Bergamot 构建留到确认可获取全部子模块后再评估。

## 本轮已落地

- 底部导航：同传 / 记录 / 我的。
- 记录：自动保存、搜索、星标、重命名、删除、Markdown 导出/分享。
- 我的：用量、TTS、显示原文、字号、AI 关键词/上下文、引擎设置、模型下载、文本翻译。
- 同传：单通道/双通道/语音转文字/聊天室入口；TTS 快捷开关；AI 纪要。
- AI 关键词/上下文已注入云端翻译提示词。
