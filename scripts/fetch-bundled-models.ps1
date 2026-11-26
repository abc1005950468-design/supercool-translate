param([switch]$Force)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $root "android/app/src/main/assets/models"

$packs = @(
  @{ Id="ru-small"; Repo="csukuangfj/sherpa-onnx-streaming-zipformer-small-ru-vosk-int8-2025-08-16"; Files=@(
    @{Name="encoder.int8.onnx"; Bytes=26214060},
    @{Name="decoder.onnx"; Bytes=2093080},
    @{Name="joiner.int8.onnx"; Bytes=259417},
    @{Name="tokens.txt"; Bytes=6388}
  )},
  @{ Id="zh-small"; Repo="csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"; Files=@(
    @{Name="encoder-epoch-99-avg-1.int8.onnx"; Bytes=21621684},
    @{Name="decoder-epoch-99-avg-1.int8.onnx"; Bytes=1888682},
    @{Name="joiner-epoch-99-avg-1.int8.onnx"; Bytes=1795562},
    @{Name="tokens.txt"; Bytes=48697}
  )},
  @{ Id="en-small"; Repo="csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17"; Files=@(
    @{Name="encoder-epoch-99-avg-1.int8.onnx"; Bytes=42845182},
    @{Name="decoder-epoch-99-avg-1.onnx"; Bytes=539499},
    @{Name="joiner-epoch-99-avg-1.onnx"; Bytes=259572},
    @{Name="tokens.txt"; Bytes=5048}
  )},
  @{ Id="lid-tiny"; Repo="csukuangfj/sherpa-onnx-whisper-tiny"; Files=@(
    @{Name="tiny-encoder.int8.onnx"; Bytes=12937772},
    @{Name="tiny-decoder.int8.onnx"; Bytes=89855401}
  )}
)

foreach ($pack in $packs) {
  $dir = Join-Path $assetRoot $pack.Id
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  foreach ($file in $pack.Files) {
    $target = Join-Path $dir $file.Name
    if (-not $Force -and (Test-Path $target) -and ((Get-Item $target).Length -eq $file.Bytes)) {
      Write-Host "ok $($pack.Id)/$($file.Name)"
      continue
    }
    $urls = @(
      "https://hf-mirror.com/$($pack.Repo)/resolve/main/$($file.Name)",
      "https://huggingface.co/$($pack.Repo)/resolve/main/$($file.Name)"
    )
    $done = $false
    foreach ($url in $urls) {
      $tmp = "$target.part"
      try {
        Write-Host "download $($pack.Id)/$($file.Name)"
        Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing
        if ((Get-Item $tmp).Length -ne $file.Bytes) { throw "size mismatch" }
        Move-Item -LiteralPath $tmp -Destination $target -Force
        $done = $true
        break
      } catch {
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
      }
    }
    if (-not $done) { throw "Failed to download $($pack.Id)/$($file.Name)" }
  }
}

Write-Host "Bundled ASR/LID models restored under $assetRoot"
