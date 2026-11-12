# Sets up the toolchain this project needs but the machine lacks:
#   - OpenJDK 17 (Gradle 8.11.1 cannot run on the JDK 25 that Android Studio ships)
#   - Android SDK Platform 35 + Build-Tools 35.0.0 (only 37/36.0.0 are installed)
#
# Two lessons baked in:
#   1. Downloads use curl.exe: Windows PowerShell 5.1's Invoke-WebRequest renders a
#      progress bar per chunk and collapsed throughput (measured 0.05 MB/s vs 12 MB/s).
#   2. sdkmanager's Java HTTP stack cannot fetch Google's repository manifests on this
#      machine ("IO exception while downloading manifest"), so SDK packages are fetched
#      directly from dl.google.com with curl and unzipped into place.
# English-only messages on purpose: PS 5.1 mis-decodes UTF-8 .ps1 files without a BOM.

param(
    [string]$SdkDir = 'D:\Androidstudio'
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
$projDir = $PSScriptRoot
$toolsDir = Join-Path (Split-Path -Parent $projDir) 'tools'
$jdkDir = Join-Path $toolsDir 'jdk-17'
$problems = New-Object System.Collections.ArrayList

New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue

function Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Info($msg) { Write-Host "    $msg" }
function Ok($msg) { Write-Host "    OK  $msg" -ForegroundColor Green }
function Bad($msg) { Write-Host "    !!  $msg" -ForegroundColor Red; [void]$problems.Add($msg) }

function Get-File($urls, $dest, $minBytes) {
    Remove-Item $dest -Force -ErrorAction SilentlyContinue
    foreach ($u in $urls) {
        Info "curl $u"
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        & curl.exe --location --fail --retry 2 --retry-delay 2 --connect-timeout 30 --silent --show-error --output $dest $u
        $code = $LASTEXITCODE
        $sw.Stop()
        if ($code -eq 0 -and (Test-Path $dest) -and ((Get-Item $dest).Length -ge $minBytes)) {
            $mb = (Get-Item $dest).Length / 1MB
            Ok ("{0:N1} MB in {1:N0}s ({2:N1} MB/s)" -f $mb, $sw.Elapsed.TotalSeconds, ($mb / [Math]::Max($sw.Elapsed.TotalSeconds, 1)))
            return $true
        }
        Bad "curl failed (exit $code) or file too small: $u"
    }
    return $false
}

function Test-Zip($path) {
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
        $count = $zip.Entries.Count
        $zip.Dispose()
        if ($count -le 0) { Bad "zip has no entries: $path"; return $false }
        return $true
    } catch {
        Bad "zip is corrupt: $path -> $($_.Exception.Message)"
        return $false
    }
}

# Install one SDK package by downloading its official archive and unzipping it.
# $pkgPath  : e.g. 'platforms;android-35'
# $destRoot : e.g. '<sdk>\platforms'
# $destName : e.g. 'android-35'
function Install-SdkPackageFromRepo($pkgPath, $destRoot, $destName) {
    $xmlPath = Join-Path $toolsDir 'repository2-3.xml'
    if (-not (Test-Path $xmlPath)) {
        if (-not (Get-File -urls @('https://dl.google.com/android/repository/repository2-3.xml') -dest $xmlPath -minBytes 100KB)) {
            Bad "cannot download repository manifest"
            return $false
        }
    }
    $rel = $null
    $archives = @()
    try {
        [xml]$doc = [System.IO.File]::ReadAllText($xmlPath, [System.Text.Encoding]::UTF8)
        # 根元素是 <sdk-repository>（带连字符），必须用 XPath 而不是属性式访问
        $all = $doc.SelectNodes("//remotePackage")
        $node = $doc.SelectSingleNode("//remotePackage[@path='$pkgPath']")
        if (-not $node) {
            Info "manifest root: $($doc.DocumentElement.Name), remotePackage count: $($all.Count)"
            $sample = @($all | Where-Object { $_.GetAttribute('path') -like 'platforms;*' } |
                Select-Object -First 8 | ForEach-Object { $_.GetAttribute('path') })
            Info "platform paths in manifest: $($sample -join ', ')"
        } else {
            # 归档里 <host-os> 是子元素，不是属性；只看属性会误选 Linux 版
            $archives = @($node.SelectNodes('archives/archive'))
            foreach ($a in $archives) {
                $os = ''
                $osEl = $a.SelectSingleNode('host-os')
                if ($osEl) { $os = $osEl.InnerText.Trim() }
                if (-not $os) { $os = $a.GetAttribute('hostOs') }
                $urlNode = $a.SelectSingleNode('complete/url')
                if (-not $urlNode) { $urlNode = $a.SelectSingleNode('url') }
                $u = ''
                if ($urlNode) { $u = $urlNode.InnerText.Trim() }
                if ($os -eq 'windows' -or $u -like '*windows*') { $rel = $u; break }
            }
            if (-not $rel -and $archives.Count -eq 1) {
                # 平台包只有一个归档，没有 host-os
                $only = $archives[0].SelectSingleNode('complete/url')
                if (-not $only) { $only = $archives[0].SelectSingleNode('url') }
                if ($only) { $rel = $only.InnerText.Trim() }
            }
            if ($rel) { Info "archive: $rel" }
        }
    } catch {
        Bad "cannot parse manifest: $($_.Exception.Message)"
    }

    # 兜底：清单里找不到就直接按 Google 的实际命名下载（这两个名字已从
    # repository2-3.xml 中核对过：build-tools 用下划线，platform 用 _rNN 后缀）
    $direct = @()
    if ($pkgPath -eq 'platforms;android-35') {
        $direct = @('platform-35_r02.zip', 'platform-35_r01.zip')
    } elseif ($pkgPath -eq 'build-tools;35.0.0') {
        $direct = @('build-tools_r35_windows.zip')
    }
    if (-not $rel) {
        Info "no archive url from manifest (archives=$($archives.Count)), trying direct names"
        if ($direct.Count -eq 0) { Bad "nothing to try for $pkgPath"; return $false }
    }

    $zip = Join-Path $toolsDir (($pkgPath -replace '[^A-Za-z0-9]', '_') + '.zip')
    $candidates = @()
    if ($rel) { $candidates += "https://dl.google.com/android/repository/$rel" }
    foreach ($d in $direct) { $candidates += "https://dl.google.com/android/repository/$d" }
    if (-not (Get-File -urls $candidates -dest $zip -minBytes 1MB)) { return $false }
    if (-not (Test-Zip $zip)) { return $false }

    $stage = Join-Path $toolsDir 'stage'
    if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
    try {
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $stage)
    } catch {
        Bad "extract failed: $($_.Exception.Message)"
        return $false
    }
    # The archives contain exactly one top-level directory whose name may not match
    # the folder the SDK expects (build-tools zips use the API level, e.g. android-15).
    $inner = Get-ChildItem $stage -Directory | Select-Object -First 1
    if (-not $inner) {
        Bad "unexpected archive layout for $pkgPath"
        return $false
    }
    New-Item -ItemType Directory -Force -Path $destRoot | Out-Null
    $dest = Join-Path $destRoot $destName
    if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    Move-Item -LiteralPath $inner.FullName -Destination $dest
    Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item $zip -Force -ErrorAction SilentlyContinue
    Ok "installed $pkgPath -> $dest"
    return $true
}

# ---------------------------------------------------------------- 1. JDK 17
Step 'JDK 17'
$javaExe = Join-Path $jdkDir 'bin\java.exe'
if (Test-Path $javaExe) {
    Ok "already present: $jdkDir"
} else {
    $jdkZip = Join-Path $toolsDir 'openjdk17.zip'
    $urls = @(
        'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip',
        'https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_windows-x64_bin.zip',
        'https://mirrors.huaweicloud.com/openjdk/17/openjdk-17_windows-x64_bin.zip'
    )
    if ((Get-File $urls $jdkZip 150MB) -and (Test-Zip $jdkZip)) {
        try {
            [System.IO.Compression.ZipFile]::ExtractToDirectory($jdkZip, $toolsDir)
            $extracted = Get-ChildItem $toolsDir -Directory |
                Where-Object { (Test-Path (Join-Path $_.FullName 'bin\java.exe')) -and $_.FullName -ne $jdkDir } |
                Select-Object -First 1
            if ($extracted) { Move-Item -LiteralPath $extracted.FullName -Destination $jdkDir -Force }
            if (Test-Path $javaExe) {
                Ok "extracted to $jdkDir"
                Remove-Item $jdkZip -Force -ErrorAction SilentlyContinue
            } else {
                Bad 'extracted but java.exe not found'
            }
        } catch {
            Bad "extract failed: $($_.Exception.Message)"
        }
    }
}
if (Test-Path $javaExe) {
    $v = (& $javaExe -version 2>&1 | Select-Object -First 1)
    Ok "java: $v"
}

# ---------------------------------------------------- 2. Android cmdline-tools
Step 'Android cmdline-tools'
$sdkManager = Join-Path $SdkDir 'cmdline-tools\latest\bin\sdkmanager.bat'
if (Test-Path $sdkManager) {
    Ok 'already present'
} else {
    $cmdZip = Join-Path $toolsDir 'cmdline-tools.zip'
    $cmdStaging = Join-Path $toolsDir 'cmdline-staging'
    $urls = @(
        'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip',
        'https://dl.google.com/android/repository/commandlinetools-win-10406996_latest.zip'
    )
    if ((Get-File $urls $cmdZip 50MB) -and (Test-Zip $cmdZip)) {
        try {
            if (Test-Path $cmdStaging) { Remove-Item $cmdStaging -Recurse -Force }
            [System.IO.Compression.ZipFile]::ExtractToDirectory($cmdZip, $cmdStaging)
            $inner = Join-Path $cmdStaging 'cmdline-tools'
            $target = Join-Path $SdkDir 'cmdline-tools'
            New-Item -ItemType Directory -Force -Path $target | Out-Null
            if (Test-Path (Join-Path $target 'latest')) { Remove-Item (Join-Path $target 'latest') -Recurse -Force }
            Move-Item -LiteralPath $inner -Destination (Join-Path $target 'latest') -Force
            if (Test-Path $sdkManager) {
                Ok "installed to $target\latest"
                Remove-Item $cmdZip -Force -ErrorAction SilentlyContinue
                Remove-Item $cmdStaging -Recurse -Force -ErrorAction SilentlyContinue
            } else {
                Bad 'sdkmanager.bat not found after extract'
            }
        } catch {
            Bad "extract failed: $($_.Exception.Message)"
        }
    }
}

# ------------------------------------------- 3. SDK platform + build-tools
Step 'SDK packages (platforms;android-35, build-tools;35.0.0)'

if (-not (Test-Path (Join-Path $SdkDir 'platforms\android-35'))) {
    Info 'trying sdkmanager first (JDK 17 + IPv4 stack forced)'
    if (Test-Path $sdkManager) {
        $env:JAVA_HOME = $jdkDir
        $env:_JAVA_OPTIONS = '-Djava.net.preferIPv4Stack=true'
        try {
            & $sdkManager --sdk_root=$SdkDir 'platforms;android-35' 'build-tools;35.0.0' 2>&1 | Select-Object -Last 6
        } catch { Info "sdkmanager: $($_.Exception.Message)" }
        Remove-Item Env:\_JAVA_OPTIONS -ErrorAction SilentlyContinue
    }
    if (-not (Test-Path (Join-Path $SdkDir 'platforms\android-35'))) {
        Info 'sdkmanager did not deliver; installing from official archives with curl'
        [void](Install-SdkPackageFromRepo 'platforms;android-35' (Join-Path $SdkDir 'platforms') 'android-35')
    }
}

if (-not (Test-Path (Join-Path $SdkDir 'build-tools\35.0.0'))) {
    [void](Install-SdkPackageFromRepo 'build-tools;35.0.0' (Join-Path $SdkDir 'build-tools') '35.0.0')
}

if (Test-Path (Join-Path $SdkDir 'platforms\android-35')) { Ok 'platforms;android-35 present' } else { Bad 'platforms;android-35 still missing' }
if (Test-Path (Join-Path $SdkDir 'build-tools\35.0.0')) { Ok 'build-tools;35.0.0 present' } else { Bad 'build-tools;35.0.0 still missing' }

# ---------------------------------------------------- 4. local.properties
Step 'local.properties'
$sdkEscaped = $SdkDir -replace '\\', '\\'
$localProps = Join-Path $projDir 'local.properties'
[System.IO.File]::WriteAllText($localProps, "sdk.dir=$sdkEscaped`n", [System.Text.Encoding]::ASCII)
Ok "wrote $localProps"

Write-Host ''
if ($problems.Count -eq 0) {
    Write-Host 'SETUP RESULT: OK' -ForegroundColor Green
} else {
    Write-Host 'SETUP RESULT: PROBLEMS' -ForegroundColor Red
    $problems | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
}
