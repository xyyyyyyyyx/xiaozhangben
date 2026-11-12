# 小账本一键构建脚本
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File build-apk.ps1 -Check   # 只检查环境，不编译
#   powershell -NoProfile -ExecutionPolicy Bypass -File build-apk.ps1          # 编译 debug APK
#   powershell -NoProfile -ExecutionPolicy Bypass -File build-apk.ps1 -Variant Release
#
# 注意：本机 Android Studio 自带的是 JDK 25，Gradle 8.11.1 跑不了，
# 所以优先使用 setup-env.ps1 下载到 ..\tools\jdk-17 的 JDK 17。

param(
    [ValidateSet('Debug', 'Release')]
    [string]$Variant = 'Debug',
    [switch]$Check,
    [string]$ExtraArgs = ''
)

$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$requiredPlatform = 'android-35'
$requiredBuildTools = '35.0.0'

function Write-Step($text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Write-Ok($text) { Write-Host "    OK  $text" -ForegroundColor Green }
function Write-Bad($text) { Write-Host "    !!  $text" -ForegroundColor Red }

function Find-AndroidSdk {
    $candidates = New-Object System.Collections.ArrayList
    foreach ($c in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($c) { [void]$candidates.Add($c) }
    }
    [void]$candidates.Add('D:\Androidstudio')
    [void]$candidates.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
    [void]$candidates.Add('C:\Android\Sdk')
    [void]$candidates.Add('D:\Android\Sdk')
    [void]$candidates.Add('D:\Android\android-sdk')
    [void]$candidates.Add((Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'))

    foreach ($c in $candidates) {
        if ($c -and (Test-Path (Join-Path $c 'platforms'))) { return $c }
    }
    # 兜底：按内容在几个根目录下找（目录名可能是中文或自定义的）
    foreach ($root in @('D:\', 'C:\Program Files', "$env:LOCALAPPDATA\Android")) {
        if (-not (Test-Path $root)) { continue }
        $hit = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object {
                (Test-Path (Join-Path $_.FullName 'platforms')) -and
                (Test-Path (Join-Path $_.FullName 'platform-tools'))
            } | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return $c }
    }
    return $null
}

function Find-Jdk {
    $candidates = New-Object System.Collections.ArrayList
    # setup-env.ps1 下载的 JDK 17 最优先
    [void]$candidates.Add((Join-Path (Split-Path -Parent $PSScriptRoot) 'tools\jdk-17'))
    if ($env:JAVA_HOME) { [void]$candidates.Add($env:JAVA_HOME) }
    foreach ($parent in @(
            'C:\Program Files\Eclipse Adoptium',
            'C:\Program Files\Microsoft',
            'C:\Program Files\Java',
            'C:\Program Files\Zulu',
            'D:\Program Files\Eclipse Adoptium',
            'D:\Java')) {
        if (Test-Path $parent) {
            Get-ChildItem $parent -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match 'jdk' } |
                ForEach-Object { [void]$candidates.Add($_.FullName) }
        }
    }
    # Android Studio 自带的 jbr 放最后，仅作兜底
    foreach ($studio in @(
            'D:\安卓studio',
            'C:\Program Files\Android\Android Studio',
            'D:\Android Studio',
            'D:\AndroidStudio')) {
        [void]$candidates.Add((Join-Path $studio 'jbr'))
    }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path (Join-Path $c 'bin\java.exe'))) { return $c }
    }
    return $null
}

Write-Step '查找 Android SDK'
$sdk = Find-AndroidSdk
if (-not $sdk) {
    Write-Bad '没找到 Android SDK。请先运行 setup-env.ps1，或设置 ANDROID_HOME。'
    exit 2
}
Write-Ok "SDK: $sdk"

$platformsDir = Join-Path $sdk 'platforms'
$buildToolsDir = Join-Path $sdk 'build-tools'
$hasPlatform = Test-Path (Join-Path $platformsDir $requiredPlatform)
$hasBuildTools = Test-Path (Join-Path $buildToolsDir $requiredBuildTools)

if ($hasPlatform) { Write-Ok "SDK Platform $requiredPlatform 已安装" }
else { Write-Bad "缺少 SDK Platform $requiredPlatform（Android 15）" }

if ($hasBuildTools) { Write-Ok "Build-Tools $requiredBuildTools 已安装" }
else { Write-Bad "缺少 Build-Tools $requiredBuildTools" }

if (-not $hasPlatform -or -not $hasBuildTools) {
    Write-Host ''
    Write-Host '请先运行：' -ForegroundColor Yellow
    Write-Host '  powershell -NoProfile -ExecutionPolicy Bypass -File setup-env.ps1' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '已安装的 platforms：' -NoNewline
    if (Test-Path $platformsDir) { (Get-ChildItem $platformsDir -Directory | Select-Object -ExpandProperty Name) -join ', ' } else { '（无）' }
    Write-Host '已安装的 build-tools：' -NoNewline
    if (Test-Path $buildToolsDir) { (Get-ChildItem $buildToolsDir -Directory | Select-Object -ExpandProperty Name) -join ', ' } else { '（无）' }
    exit 3
}

Write-Step '查找 JDK'
$jdk = Find-Jdk
if (-not $jdk) {
    Write-Bad '没找到可用的 JDK。请先运行 setup-env.ps1 下载 JDK 17。'
    exit 4
}
$javaExe = Join-Path $jdk 'bin\java.exe'
# java -version 把版本写到 stderr，PS 5.1 会当成错误；这里临时放开免得被 Stop 中止
$savedEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersion = (& $javaExe -version 2>&1 | Select-Object -First 1)
$ErrorActionPreference = $savedEap
Write-Ok "JDK: $jdk"
Write-Ok "$javaVersion"

Write-Step '生成 local.properties'
# Java properties 格式：反斜杠要转义，文件不能带 BOM
$sdkEscaped = $sdk -replace '\\', '\\'
$localProperties = Join-Path $projectDir 'local.properties'
[System.IO.File]::WriteAllText($localProperties, "sdk.dir=$sdkEscaped`n", [System.Text.Encoding]::ASCII)
Write-Ok "已写入 $localProperties"

if ($Check) {
    Write-Host ''
    Write-Host '环境检查通过，可以编译了。' -ForegroundColor Green
    exit 0
}

Write-Step "开始构建 assemble$Variant（首次会下载 Gradle 与依赖，比较慢）"
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
# Gradle 默认把缓存写到 C:\Users\<用户>\.gradle，那里在当前沙箱下不可写；
# 放到工作区内的 tools\gradle-home，既省系统盘又不受权限影响。
$gradleHome = Join-Path (Split-Path -Parent $projectDir) 'tools\gradle-home'
New-Item -ItemType Directory -Force -Path $gradleHome | Out-Null
$env:GRADLE_USER_HOME = $gradleHome
Write-Ok "GRADLE_USER_HOME=$gradleHome"
# AGP 也会往 C:\Users\<用户>\.android 写缓存与统计，一并挪进工作区。
# 注意：AGP 只接受一种注入方式，同时设 ANDROID_SDK_HOME / ANDROID_PREFS_ROOT
# 会直接抛 AndroidLocationsException，所以这里只设 ANDROID_USER_HOME。
$androidHome = Join-Path (Split-Path -Parent $projectDir) 'tools\android-home'
New-Item -ItemType Directory -Force -Path $androidHome | Out-Null
$env:ANDROID_USER_HOME = $androidHome
Write-Ok "ANDROID_USER_HOME=$androidHome"
# Kotlin 编译器要在系统临时目录里建 .alive 文件，那里会被沙箱拒绝
# （AccessDeniedException: ...\Temp\kotlin-compiler-*.alive），一并挪进工作区
$tmpDir = Join-Path (Split-Path -Parent $projectDir) 'tools\tmp'
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
$env:TEMP = $tmpDir
$env:TMP = $tmpDir
Write-Ok "TEMP=$tmpDir"

$gradlew = Join-Path $projectDir 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    Write-Bad "找不到 $gradlew"
    exit 5
}

Push-Location $projectDir
try {
    # Gradle 也会往 stderr 写警告，这里必须放开 Stop，改为看退出码
    $ErrorActionPreference = 'Continue'
    $extra = @()
    if ($ExtraArgs) { $extra = $ExtraArgs.Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries) }
    & $gradlew --no-daemon @extra "assemble$Variant"
    $code = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $savedEap
    Pop-Location
}

if ($code -ne 0) {
    Write-Bad "构建失败，退出码 $code"
    exit $code
}

$apkDir = Join-Path $projectDir "app\build\outputs\apk\$($Variant.ToLower())"
Write-Host ''
Write-Step '构建成功，产物：'
Get-ChildItem $apkDir -Filter *.apk -ErrorAction SilentlyContinue |
    ForEach-Object { Write-Host "    $($_.FullName)  ($([math]::Round($_.Length / 1MB, 2)) MB)" -ForegroundColor Green }
