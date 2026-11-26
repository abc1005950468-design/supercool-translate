# 最终测试报告（M1 端到端）

> 日期：2026-10-19
> 设备：Android 35 模拟器（x86_64，WHPX 加速，无窗口）
> APK：`android/app/build/outputs/apk/debug/app-debug.apk`（约 210 MB，含全 ABI 与 sherpa）

## 测试环境

- 模拟器：`offline_test`，Android 35 google_apis x86_64。
- 本地模型：`ru-small`（29 MB），应用内下载到 `files/models/ru-small`。
- 本地翻译：ML Kit `ru → zh`。
- 云端翻译：本机 Strata `qwen3.8-flash-next-iq3_xxs`，模拟器通过 `http://10.0.2.2:8080/v1` 访问。
- 测试音频：内置 `test_ru.wav`（7.08 s，16 kHz 单声道 PCM16）。

## 结果

| # | 测试 | 结果 | 证据 |
| --- | --- | --- | --- |
| T1 | APK 安装与启动 | **PASS** | 启动无崩溃，界面正常 |
| T2 | 应用内模型下载 | **PASS** | 4 个文件、字节数全部正确 |
| T3 | 本地俄语流式识别 | **PASS** | 完整识别出测试句 |
| T4 | ML Kit 俄→中翻译 | **PASS** | 中文译文正常显示 |
| T5 | 云端 API 俄→中翻译 | **PASS** | 中文译文正常显示 |
| T6 | 崩溃回归 | **PASS** | 修复 AssetManager 崩溃后无 FATAL |

### T3 识别原文

```
родион потапыч высчитывал каждый новый вершок углубления и давно определил про себя
```

### T4 ML Kit 译文

```
罗季翁·波塔佩奇精确计算着每一寸新的下陷深度，并且早已在心中做出了判断
```

### T5 云端译文

```
罗季翁·波塔佩奇精确计算着每一寸新的下沉深度，并早已在心中做出判断
```

## 测试中发现并修复的问题

1. **sherpa 绝对路径 + AssetManager 导致原生崩溃**
   修复：`OnlineRecognizer(assetManager = null, config = config)`。
2. **取消传播错误**
   partial 翻译被取消时，`FallbackTranslator` 把 `CancellationException` 当成失败并回退 ML Kit，导致卡死。
   修复：取消直接向上抛出；云端模式不做 partial 翻译。
3. **ML Kit 首次下载无超时**
   修复：`Tasks.await(..., 25/30, TimeUnit.SECONDS)`，失败时显示翻译失败而不是无限等待。
4. **ASR 加载持有锁过久**
   修复：独立 `loadLock` + generation 校验，避免加载期间阻塞音频线程。

## 已知限制

- `ru-small` 输出小写、无标点；Whisper 复听与标点恢复尚未接入（M2/M6）。
- ML Kit 首次使用需要下载语言模型；无 GMS/网络时会超时，需改用云端或等待下载。
- 中英 `zh-en` 模型已接入目录，但本轮未做端到端测试。
- 尚未做真机 8 GB 机型的功耗、发热、长时间稳定性测试。
- 云端 API Key 的加密存储在模拟器上生效，但尚未做真机 Keystore 兼容性测试。

## 证据文件

- 最终界面截图：`work/screenshots/final-e2e.png`
- 云端成功截图：`work/screenshots/cloud-success.png`
- ML Kit 成功截图：`work/screenshots/mlkit-path.png`
- 最终 UI 树：`work/window-final.xml`
- 云端日志：`adb logcat` 中 `CloudTranslator` HTTP 200

## 结论

M1 端到端闭环成立：

**应用内下载模型 → 本地俄语流式识别 → ML Kit / 云端翻译 → 双语文稿展示**

可以进入下一阶段：Whisper 准确模式、真机性能测试、历史记录与 TTS。
