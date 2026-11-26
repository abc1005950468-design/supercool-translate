# M0 决策：俄语 ASR 选型（2026-10-19）

## 环境

- PC：RTX 4090 Laptop 16GB / 64GB RAM
- sherpa-onnx 1.13.8（与 Android AAR 同版本）
- CPU 4 线程，16 kHz 单声道 PCM16
- 测试音频：`work/models/ru-small/test_wavs/0.wav`（9.24 s）、`1.wav`（7.08 s）

## 候选与实测

| 模型 | 体积 | 首字 | 处理 7–9 s 音频 | 文本 |
| --- | --- | --- | --- | --- |
| `streaming-zipformer-small-ru-vosk-int8-2025-08-16` | 29 MB | 22–162 ms | 0.16–0.29 s | 全对，但小写、无标点 |
| `whisper-small` int8 | 375 MB | 句末 | 1.0–2.5 s | 全对，带大小写与标点 |
| 8 语流式 zipformer（ar/en/id/ja/ru/th/vi/zh） | 340 MB | 未测 | 未测 | 第二阶段候选，首发不需要 |

### 关键发现

1. 俄语小模型在 1.wav 上最初漏掉结尾 “про себя”。
2. 在停止前补 **0.5 秒静音** 再 `input_finished()`，结尾完整找回，与 Whisper 文本一致。
3. 小模型输出小写、无标点；Whisper 输出大小写与标点完整。
4. 两个模型在两句测试音频上内容一致，可作为交叉验证。

## 决策

采用 **双通道 ASR**：

```
麦克风 16 kHz PCM
  ├─ 快通道：ru-small 流式（29 MB）
  │    · 100 ms 分块，实时 partial
  │    · 停止/断句前补 500 ms 静音再 flush
  │    · 立即送 ML Kit 快译，首字优先
  └─ 准通道：Whisper-small int8（375 MB，可选下载）
       · 定稿后在后台复听同一段音频
       · 用带标点的结果替换源文，并重新翻译
       · 队列超过 2 条时跳过，保证不积压
```

- 默认“速度优先”：只跑快通道。
- 用户开启“准确模式”：启用 Whisper 复听；不阻塞首版译文。
- 中文/英文：沿用 `streaming-zipformer-bilingual-zh-en-2023-02-20`（189 MB）。
- 8 语流式模型暂不作为首发依赖，留给日/韩/法/德/西扩展。

## 模型包

| 包 | 文件 | 体积 |
| --- | --- | --- |
| 俄语快通道 | encoder.int8 / decoder / joiner.int8 / tokens | 29 MB |
| 俄语准通道 | whisper-small encoder+decoder int8 / tokens | 375 MB |
| 中英 | 中英 zipformer 四件套 | 189 MB |

侧载分发，允许用户只下俄语包；准确模式再下 Whisper 包。

## 风险

- Whisper 在手机上可能 3–5 s/句，需真机压测；积压时自动跳过。
- 小模型无标点，适合字幕但不适合正式文稿；准确模式解决。
- 模型许可需在分发前核对（Vosk 系通常 Apache-2.0，Whisper MIT）。

## 下一步

1. Android 端接 `OnlineRecognizer` + 500 ms 尾音 flush。
2. 接 `OfflineRecognizer` Whisper 复听（可选）。
3. 建立俄语术语表与标点/大小写展示规则。
4. 真机测 8 GB 机型的首字、整句与内存。

## 原始数据

`work/asr-results.jsonl`
