# 二进制依赖

首版远端源码不直接包含大体积二进制和模型缓存。固定版本依赖包已发布为 GitHub Release：`deps-3.5-v1`。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/fetch-dependencies.ps1
```

依赖文件如下：

| 文件 | 用途 | 大小 |
| --- | --- | ---: |
| `android/app/libs/sherpa-onnx-jni.aar` | Sherpa JNI | 17,326,307 B |
| `android/app/libs/tesseract4android-openmp-4.9.0.aar` | Tesseract OCR | 14,081,889 B |
| `android/app/src/main/jniLibs/arm64-v8a/libonnxruntime.so` | ONNX Runtime arm64 | 22,249,560 B |
| `android/app/src/main/jniLibs/x86_64/libonnxruntime.so` | ONNX Runtime x86_64 | 25,581,120 B |
| `android/app/src/main/assets/tessdata/chi_sim.traineddata` | 中文 OCR | 13,077,423 B |
| `android/app/src/main/assets/tessdata/rus.traineddata` | 俄文 OCR | 3,861,738 B |

内置 ASR/LID 模型通过：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/fetch-bundled-models.ps1
```

Release:
https://github.com/abc1005950468-design/supercool-translate/releases/tag/deps-3.5-v1
