# [P0] NLLB 内存强退

## 目标

让 NLLB 600M 在 Nubia NX769J 等单进程内存限制较严格的设备上稳定运行，或明确降级到可用的小模型/云端引擎。

## 当前证据

- `ApplicationExitInfo`: `SIGNALED / status=9`
- 无 Java FATAL EXCEPTION
- 单句翻译也观察到 4–5 GB 级 Native/ONNX 峰值
- Java Heap 约几十 MB，非主要来源

## 允许修改

- `android/app/src/main/cpp/nllb_ort.cpp`
- `android/app/src/main/java/com/mine/offlinetranslate/mt/Nllb*`
- `LiveViewModel.kt`
- `ModelSpec.kt`
- 相关测试和 docs

## 禁止修改

- 不牺牲已有 OCR、API Key 持久化和记录功能
- 不提交模型或 APK
- 不在没有真机日志的情况下宣称修复

## 验收

- 单句中文→俄语完成且进程存活
- 连续中文语音 10 分钟进程存活
- 停止同传后 20 秒 PSS 明显下降
- 提供 `dumpsys meminfo`、`ActivityManagerExitInfo` 和 logcat

## 回滚

恢复 `nllb_ort.cpp` 上一次已验证提交，并保留原模型下载配置。
