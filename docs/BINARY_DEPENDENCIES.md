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

## TASK-20261126-0013 Windows clean-machine verification

Verification checkout: `24a3b22f8a61e690b49baad8e35dffd17f6817f5`. Clone and branch creation exited 0 and the starting worktree was clean. C: had 157,651,931,136 bytes free. Java/JDK, Gradle and Android SDK were unavailable on PATH or at their standard installation paths. JAVA_HOME, ANDROID_HOME and ANDROID_SDK_ROOT were unset. The repository contained no Gradle Wrapper. The Android build specifies SDK 35, NDK 27.0.12077973 and CMake 3.22.1.

| Command | Exit | Evidence |
| --- | --- | --- |
| `powershell -ExecutionPolicy Bypass -File scripts/fetch-dependencies.ps1` | 0 | Fresh download and cached reruns verified the release ZIP and all six native/OCR files. An earlier CDN request failed with exit 1. |
| `powershell -ExecutionPolicy Bypass -File scripts/fetch-bundled-models.ps1` | Not run | Its destination is the task-prohibited `android/app/src/main/assets/models/` directory. |
| `gradle -p android testDebugUnitTest` | 1 | cmd.exe could not resolve Gradle. Unit tests did not start. |
| `gradle -p android assembleDebug` | 1 | cmd.exe could not resolve Gradle. The build did not start. |

The release ZIP was 61,736,129 bytes and matched the published SHA-256 `5f9ebb325298f639ed2ed25678620de0bbc7697b0ae412577e51912dd127165e`. The restore script verifies the archive and the manifest entries for the six destination files before copying. It uses a verified cache under Git metadata and supports `-Force` for a fresh download. With `GH_TOKEN` or `GITHUB_TOKEN` set, GitHub receives the credential and the release CDN receives only the signed download URL. The only downloaded payload was the pinned release ZIP. The six restored payloads are Git-ignored.

The expected APK path, `android/app/build/outputs/apk/debug/app-debug.apk`, did not exist. The 300 MB limit remains unverified. The model script currently checks byte counts only. Model SHA-256, model-script repeatability, Android unit tests and the APK size remain open.
