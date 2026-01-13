[CmdletBinding()]
param(
  [switch]$Clean,
  [switch]$Offline,
  [switch]$Stacktrace
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
  Write-Error $Message
  exit 1
}

function Unescape-JavaPropertiesValue([string]$Value) {
  if ($null -eq $Value) { return $null }
  $v = $Value
  $v = $v.Replace('\:', ':').Replace('\=', '=')
  $v = $v.Replace('\\\\', '\')
  return $v
}

function Escape-JavaPropertiesValue([string]$Value) {
  if ([string]::IsNullOrWhiteSpace($Value)) { return $Value }
  $v = $Value
  $v = $v.Replace('\', '\\')
  $v = $v.Replace(':', '\:')
  return $v
}

function Get-AndroidSdkDir([string]$RepoRoot) {
  if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) { return $env:ANDROID_SDK_ROOT }
  if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) { return $env:ANDROID_HOME }

  $localProps = Join-Path $RepoRoot 'local.properties'
  if (-not (Test-Path $localProps)) { return $null }

  $line = (Get-Content -LiteralPath $localProps -ErrorAction Stop) | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
  if (-not $line) { return $null }

  $raw = ($line -replace '^\s*sdk\.dir\s*=\s*', '').Trim()
  return (Unescape-JavaPropertiesValue $raw)
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $RepoRoot

$Gradlew = Join-Path $RepoRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $Gradlew)) {
  Fail "Gradle wrapper not found: $Gradlew"
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
  Fail "java not found; install JDK (recommended 17) and ensure java is on PATH or set JAVA_HOME."
}

$SdkDir = Get-AndroidSdkDir -RepoRoot $RepoRoot
if ([string]::IsNullOrWhiteSpace($SdkDir)) {
  Fail "Android SDK not found; set ANDROID_SDK_ROOT (recommended) or create $RepoRoot\\local.properties with sdk.dir=... (Android Studio can generate it)."
}

if (-not (Test-Path -LiteralPath $SdkDir)) {
  Fail "Android SDK path does not exist: $SdkDir"
}

$LocalPropsPath = Join-Path $RepoRoot 'local.properties'
if (-not (Test-Path -LiteralPath $LocalPropsPath)) {
  "sdk.dir=$(Escape-JavaPropertiesValue $SdkDir)" | Out-File -LiteralPath $LocalPropsPath -Encoding ascii -Force
}

$AndroidJar = Join-Path $SdkDir 'platforms\android-31\android.jar'
if (-not (Test-Path -LiteralPath $AndroidJar)) {
  Fail "Missing Android SDK Platform 31 (platforms;android-31); install it via sdkmanager and retry."
}

$BuildToolsDir = Join-Path $SdkDir 'build-tools'
if (-not (Test-Path -LiteralPath $BuildToolsDir)) {
  Fail "Missing Android Build Tools (build-tools;xx.x.x); install it via sdkmanager and retry."
}
$BuildToolsVersions = Get-ChildItem -LiteralPath $BuildToolsDir -Directory -ErrorAction SilentlyContinue
if (-not $BuildToolsVersions -or $BuildToolsVersions.Count -lt 1) {
  Fail "build-tools directory is empty: $BuildToolsDir; install build-tools via sdkmanager and retry."
}

Write-Host "RepoRoot: $RepoRoot"
Write-Host "Android SDK: $SdkDir"
$JavaVersionLine = (& cmd /c 'java -version 2>&1' | Select-Object -First 1)
Write-Host "Java: $JavaVersionLine"

$GradleArgs = @()
if ($Clean) { $GradleArgs += 'clean' }
$GradleArgs += ':nga_phone_base_3.0:assembleDebug'
$GradleArgs += '--no-daemon'
if ($Offline) { $GradleArgs += '--offline' }
if ($Stacktrace) { $GradleArgs += '--stacktrace' }

$prevErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& $Gradlew @GradleArgs
$ErrorActionPreference = $prevErrorActionPreference
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$ApkDir = Join-Path $RepoRoot 'nga_phone_base_3.0\build\outputs\apk\debug'
if (-not (Test-Path -LiteralPath $ApkDir)) {
  Fail "Build finished but output directory not found: $ApkDir"
}

$Apk = Get-ChildItem -LiteralPath $ApkDir -Filter '*.apk' -File -Recurse |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $Apk) {
  Fail "Build finished but APK not found under: $ApkDir"
}

Write-Host "APK: $($Apk.FullName)"
