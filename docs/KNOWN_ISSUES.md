# 已知问题

## P0：NLLB 在 Nubia NX769J 被系统强杀

- 症状：识别后秒退，无 `FATAL EXCEPTION`
- 证据：`ApplicationExitInfo` 为 `SIGNALED / status=9`
- 最小对照：仅翻译短句也可能达到约 4–5 GB Native 峰值
- 约束：Java Heap 小，主要占用在 NLLB/ONNX Runtime
- 下一步：小模型、进程隔离或默认云端/可选引擎，不能继续只调线程参数

## P1：API Key 持久化待完整真机回归

- 已实现输入即保存、切离线不清空
- 待验证：保存、切离线、杀进程、重启、重新进入设置

## P1：Android 包体较大

- 当前约 217 MB
- 内置 OCR、ASR、ONNX Runtime 和原生库占主要空间
- 模型资产通过脚本恢复，不应提交 Git

## P2：多机型覆盖不足

- 最低 Android 8
- 需要 8/12/16 GB 内存与无 GMS 设备矩阵
