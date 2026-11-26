# 内置模型安装包（侧载用）

## 生成方式

模型不进 Git（`.gitignore` 已忽略 `android/app/src/main/assets/models/`），打包前先把它们放进 assets：

```
android/app/src/main/assets/models/
  ru-small/            encoder.int8.onnx  decoder.onnx  joiner.int8.onnx  tokens.txt
  zh-en/               encoder-epoch-99-avg-1.int8.onnx  decoder-...  joiner-...  tokens.txt
  lid-tiny/            tiny-encoder.int8.onnx  tiny-decoder.int8.onnx
  speaker-embedding/   wespeaker_en_voxceleb_resnet34_LM.onnx
```

来源：`work/models/`（下载脚本与 `ModelCatalog` 一致，走 hf-mirror）。

```
gradle -p android assembleDebug
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`，当前约 476 MB。

## 首次启动

`LiveViewModel.installBundledModels()` 在首帧后检查 `files/models/<id>`，缺哪个就从 assets 解哪个，
进度写入 `downloads` 状态（“我的 → 模型下载”可见）。完成后 `refreshModel()` 自动加载 A 引擎。

一台手机需要约 340 MB 应用数据空间，加安装包本身约 820 MB 可用空间。

## 已知边界

- 调试签名，侧载安装；未做混淆与体积裁剪（4 个 ABI 的 sherpa 原生库都在）。
- ML Kit 语言包仍需 Google 服务下载；没有 GMS 或模拟器上会失败。已改为 8 秒内失败，
  并在配置了 OpenAI 兼容地址时自动回退到该地址（`TranslationEngines`）。
