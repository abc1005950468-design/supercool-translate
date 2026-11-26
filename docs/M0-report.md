# M0 报告（2026-10-19）

## 目标

1. 确定俄语 ASR 路线。
2. 建立可构建的 Android 空壳。
3. 把所有工程文件放到 D 盘。

## 结果

### 俄语 ASR

| 模型 | 体积 | 首字 | 7–9 s 音频处理 | 文本质量 |
| --- | --- | --- | --- | --- |
| ru-small 流式 | 29 MB | 22–162 ms | 0.16–0.29 s | 全对，小写无标点 |
| Whisper-small int8 | 375 MB | 句末 | 1.0–2.5 s | 全对，带标点 |

关键修复：停止前补 **0.5 s 静音** 再 flush，否则流式模型会漏掉最后一个短语。

决策：**双通道**——ru-small 负责实时 partial 与首译，Whisper 负责后台复听与标点；默认速度优先，准确模式可开。

详见 `docs/decisions/asr-ru.md` 与 `docs/decisions/asr-ru-measurements.jsonl`。

### 工程

- 路径：`D:\CodexProjects\offline-translate`
- Android：`android/`，Kotlin + Compose，单模块空壳。
- 构建：`BUILD SUCCESSFUL`，产物 `app-debug.apk`（208 MB，含全 ABI 与 sherpa）。
- 旧骨架：移到 `work/spike/offlinetranslate/`，未删除。
- 构建环境：JDK 17 + `C:\gtmp`。

### 目录

```
D:\CodexProjects\offline-translate
├─ android\      Android 工程
├─ work\         校准、模型、venv、spike
└─ docs\         方案、评估、决策、本报告
```

## 未完成 / 下一步

1. M1：Android 端接 ru-small `OnlineRecognizer` + 500 ms 尾音 flush。
2. M1：接 Whisper `OfflineRecognizer` 复听（可选下载）。
3. M1：中英 zipformer 接入 + 语言包管理。
4. M1：前台服务、AudioRecord、双语文稿 UI。
5. 真机（8 GB）压测首字、整句、内存、发热。
6. 把 `D:\CodexProjects` 加入 Codex 工作区根目录，后续补丁可直接编辑；当前 D 盘改动需要逐次授权。
