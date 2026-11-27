param([switch]$Force)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$repo = "abc1005950468-design/supercool-translate"
$url = "https://github.com/$repo/releases/download/deps-3.5-v1/supercool-translate-deps-3.5-v1.zip"
$archiveHash = "5f9ebb325298f639ed2ed25678620de0bbc7697b0ae412577e51912dd127165e"

$files = [ordered]@{
  "sherpa-onnx-jni.aar" = "android/app/libs/sherpa-onnx-jni.aar"
  "tesseract4android-openmp-4.9.0.aar" = "android/app/libs/tesseract4android-openmp-4.9.0.aar"
  "jniLibs/arm64-v8a/libonnxruntime.so" = "android/app/src/main/jniLibs/arm64-v8a/libonnxruntime.so"
  "jniLibs/x86_64/libonnxruntime.so" = "android/app/src/main/jniLibs/x86_64/libonnxruntime.so"
  "tessdata/chi_sim.traineddata" = "android/app/src/main/assets/tessdata/chi_sim.traineddata"
  "tessdata/rus.traineddata" = "android/app/src/main/assets/tessdata/rus.traineddata"
}

$gitDir = git -C $root rev-parse --absolute-git-dir
if ($LASTEXITCODE -ne 0) { throw "Cannot locate Git metadata for dependency staging." }
$stage = Join-Path $gitDir ("dependency-restore-" + [guid]::NewGuid())
$zip = Join-Path $stage "dependencies.zip"
$extract = Join-Path $stage "extracted"
$cache = Join-Path $gitDir "supercool-translate-deps-3.5-v1.zip"
$client = New-Object Net.WebClient
$client.Proxy = $null
$client.Headers["User-Agent"] = "supercool-translate-dependency-restore"
try {
  New-Item -ItemType Directory -Path $stage | Out-Null
  if (-not $Force -and (Test-Path -LiteralPath $cache) -and
      (Get-FileHash -Algorithm SHA256 -LiteralPath $cache).Hash -eq $archiveHash) {
    Copy-Item -LiteralPath $cache -Destination $zip
  } else {
    Write-Host "Downloading dependency bundle..."
    $token = $env:GH_TOKEN
    if (-not $token) { $token = $env:GITHUB_TOKEN }
    if ($token) {
      # Resolve the signed asset URL without forwarding GitHub credentials to the CDN.
      $request = [Net.HttpWebRequest]::Create("https://api.github.com/repos/$repo/releases/assets/796266618")
      $request.Proxy = $null
      $request.UserAgent = "supercool-translate-dependency-restore"
      $request.Accept = "application/octet-stream"
      $request.Headers["Authorization"] = "Bearer $token"
      $request.AllowAutoRedirect = $false
      $response = $request.GetResponse()
      try {
        if ([int]$response.StatusCode -ne 302) { throw "GitHub did not return a release asset redirect." }
        $assetUrl = [uri]$response.Headers["Location"]
        if ($assetUrl.Scheme -ne "https") { throw "GitHub release asset redirect must use HTTPS." }
      } finally {
        $response.Dispose()
      }
      try { $client.DownloadFile($assetUrl, $zip) } catch {
        $reason = $_.Exception.GetBaseException()
        if ($reason -is [Net.WebException] -and $reason.Response) {
          throw "GitHub release asset download failed (HTTP $([int]$reason.Response.StatusCode))."
        }
        throw "GitHub release asset download failed ($($reason.GetType().Name))."
      }
    } else {
      $client.DownloadFile($url, $zip)
    }
  }
  if ((Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash -ne $archiveHash) {
    throw "Dependency bundle SHA-256 mismatch."
  }
  Write-Host "Dependency bundle SHA-256 verified: $archiveHash"
  Expand-Archive -LiteralPath $zip -DestinationPath $extract

  $hashes = @{}
  foreach ($line in Get-Content -LiteralPath (Join-Path $extract "SHA256SUMS.txt")) {
    if ($line -match '^([0-9A-Fa-f]{64})\s+(.+)$') {
      $relativePath = $Matches[2].Replace('\', '/')
      if ($hashes.ContainsKey($relativePath)) { throw "Duplicate SHA-256 entry: $relativePath" }
      $hashes[$relativePath] = $Matches[1]
    }
  }

  foreach ($relativePath in $files.Keys) {
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $extract $relativePath)).Hash
    if ($actual -ne $hashes[$relativePath]) { throw "Missing or mismatched SHA-256: $relativePath" }
    Write-Host "SHA-256 verified: $relativePath $actual"
  }
  foreach ($relativePath in $files.Keys) {
    $destination = Join-Path $root $files[$relativePath]
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    Copy-Item -LiteralPath (Join-Path $extract $relativePath) -Destination $destination -Force
  }
  Copy-Item -LiteralPath $zip -Destination $cache -Force
  Write-Host "Native and OCR dependencies restored."
} finally {
  $client.Dispose()
  if (Test-Path -LiteralPath $stage) {
    [IO.Directory]::Delete($stage, $true)
  }
}
