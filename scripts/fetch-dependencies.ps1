param([switch]$Force)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$url = "https://github.com/abc1005950468-design/supercool-translate/releases/download/deps-3.5-v1/supercool-translate-deps-3.5-v1.zip"
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) "supercool-translate-deps-3.5-v1.zip"
$extract = Join-Path ([System.IO.Path]::GetTempPath()) "supercool-translate-deps-3.5-v1"

if ($Force -or -not (Test-Path $tmp)) {
  Write-Host "Downloading dependency bundle..."
  Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing
}

if (Test-Path $extract) { Remove-Item -LiteralPath $extract -Recurse -Force }
New-Item -ItemType Directory -Force -Path $extract | Out-Null
Expand-Archive -LiteralPath $tmp -DestinationPath $extract -Force

$libs = Join-Path $root "android/app/libs"
$jni = Join-Path $root "android/app/src/main/jniLibs"
$tess = Join-Path $root "android/app/src/main/assets/tessdata"
New-Item -ItemType Directory -Force -Path $libs,(Join-Path $jni "arm64-v8a"),(Join-Path $jni "x86_64"),$tess | Out-Null

Copy-Item -LiteralPath (Join-Path $extract "sherpa-onnx-jni.aar") -Destination (Join-Path $libs "sherpa-onnx-jni.aar") -Force
Copy-Item -LiteralPath (Join-Path $extract "tesseract4android-openmp-4.9.0.aar") -Destination (Join-Path $libs "tesseract4android-openmp-4.9.0.aar") -Force
Copy-Item -LiteralPath (Join-Path $extract "jniLibs/arm64-v8a/libonnxruntime.so") -Destination (Join-Path $jni "arm64-v8a/libonnxruntime.so") -Force
Copy-Item -LiteralPath (Join-Path $extract "jniLibs/x86_64/libonnxruntime.so") -Destination (Join-Path $jni "x86_64/libonnxruntime.so") -Force
Copy-Item -LiteralPath (Join-Path $extract "tessdata/chi_sim.traineddata") -Destination (Join-Path $tess "chi_sim.traineddata") -Force
Copy-Item -LiteralPath (Join-Path $extract "tessdata/rus.traineddata") -Destination (Join-Path $tess "rus.traineddata") -Force

$sumFile = Join-Path $extract "SHA256SUMS.txt"
foreach ($line in Get-Content -LiteralPath $sumFile) {
  if ($line -notmatch '^([0-9A-Fa-f]{64})\s+(.+)$') { continue }
  $expected = $Matches[1].ToUpperInvariant()
  $rel = $Matches[2].Replace('/', [IO.Path]::DirectorySeparatorChar)
  $target = Join-Path $extract $rel
  $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash.ToUpperInvariant()
  if ($actual -ne $expected) { throw "SHA-256 mismatch: $rel" }
}

Write-Host "Native and OCR dependencies restored."
