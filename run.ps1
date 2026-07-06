#Requires -Version 7.0
<#
.SYNOPSIS
    Android 极速构建、调试、发布与性能优化终端 (PowerShell 7+)
.DESCRIPTION
    集成环境自检、一键 Debug/Release、Logcat 过滤、设备管理。
    核心特色：内置 Gradle 性能优化引擎，自动配置 parallel 并行编译，解决命令行比 AS 慢的问题。
#>

param(
    [ValidateSet("Menu", "Debug", "Release", "Clean", "Logcat", "Devices", "Optimize", "Scan")]
    [string]$Action = "Menu"
)

# ================= 全局配置与颜色定义 =================
$Host.UI.RawUI.WindowTitle = "Android 极速构建终端 (满血版)"
$ColorSuccess = "Green"
$ColorError = "Red"
$ColorWarning = "Yellow"
$ColorInfo = "Cyan"
$ColorHighlight = "Magenta"

$GradleWrapper = if ($IsWindows) { ".\gradlew.bat" } else { "./gradlew" }
$GradlePropsFile = ".\gradle.properties"

# ================= 核心辅助函数 =================
function Write-Step($msg) { Write-Host "`n▶ $msg" -ForegroundColor $ColorInfo }
function Write-Success($msg) { Write-Host "✅ $msg" -ForegroundColor $ColorSuccess }
function Write-Err($msg) { Write-Host "❌ $msg" -ForegroundColor $ColorError }
function Write-Warn($msg) { Write-Host "⚠️ $msg" -ForegroundColor $ColorWarning }

function Test-Environment {
    Write-Step "正在进行环境健康检查..."
    $envOk = $true

    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Write-Err "未找到 java 命令！请检查 JAVA_HOME 环境变量。"
        $envOk = $false
    } else { Write-Success "Java 环境正常" }

    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        Write-Err "未找到 adb 命令！请检查 platform-tools 环境变量。"
        $envOk = $false
    } else { Write-Success "ADB 环境正常" }

    if (-not (Test-Path $GradleWrapper)) {
        Write-Err "当前目录下未找到 $GradleWrapper！请确保你在 Android 项目根目录运行此脚本。"
        $envOk = $false
    } else { Write-Success "Gradle Wrapper 存在" }

    if (-not $envOk) {
        Write-Err "环境检查未通过，脚本终止。"
        exit 1
    }
}

function Select-TargetDevice {
    Write-Step "正在检测连接的 Android 设备..."
    $devicesRaw = adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
    
    if (-not $devicesRaw) {
        Write-Err "未检测到任何已授权的 Android 设备！请检查 USB 连接、驱动和手机上的授权弹窗。"
        return $null
    }

    $devices = $devicesRaw | ForEach-Object {
        $parts = $_ -split '\s+'
        [PSCustomObject]@{ Serial = $parts[0]; Status = $parts[1] }
    }

    if ($devices.Count -eq 1) {
        $selected = $devices[0].Serial
        Write-Success "已自动选择唯一设备: $selected"
        $env:ANDROID_SERIAL = $selected
        return $selected
    }

    Write-Warn "检测到多台设备，请选择目标设备："
    for ($i = 0; $i -lt $devices.Count; $i++) {
        Write-Host "  [$($i+1)] $($devices[$i].Serial)" -ForegroundColor $ColorHighlight
    }
    
    do {
        $choice = Read-Host "请输入序号 (1-$($devices.Count))"
    } while (-not ($choice -match '^\d+$' -and [int]$choice -ge 1 -and [int]$choice -le $devices.Count))

    $selected = $devices[[int]$choice - 1].Serial
    Write-Success "已选择设备: $selected"
    $env:ANDROID_SERIAL = $selected
    return $selected
}

function Get-AppPackageName {
    $manifestPath = Get-ChildItem -Path ".\app\src\main" -Filter "AndroidManifest.xml" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($manifestPath) {
        $content = Get-Content $manifestPath.FullName -Raw
        if ($content -match 'package="([^"]+)"') { return $Matches[1] }
        if ($content -match "package='([^']+)'") { return $Matches[1] }
    }
    $gradlePath = ".\app\build.gradle"
    if (Test-Path $gradlePath) {
        $content = Get-Content $gradlePath -Raw
        if ($content -match 'applicationId\s+"([^"]+)"') { return $Matches[1] }
        if ($content -match "applicationId\s+'([^']+)'") { return $Matches[1] }
    }
    # 兼容 build.gradle.kts
    $gradleKtsPath = ".\app\build.gradle.kts"
    if (Test-Path $gradleKtsPath) {
        $content = Get-Content $gradleKtsPath -Raw
        if ($content -match 'applicationId\s*=\s*"([^"]+)"') { return $Matches[1] }
    }
    return $null
}

# ================= 🚀 核心性能优化模块 (解决命令行慢) =================

function Optimize-GradleProperties {
    Write-Step "正在注入 Gradle 高性能配置 (开启 Parallel 并行编译、Daemon、Caching)..."
    
    # 定义我们期望的高性能配置 (Key = Value)
    $optimizedConfigs = [ordered]@{
        "org.gradle.daemon" = "true"
        "org.gradle.parallel" = "true"
        "org.gradle.caching" = "true"
        "org.gradle.configuration-cache" = "true"
        "org.gradle.configuration-cache.parallel" = "true"
        "kotlin.incremental" = "true"
        "android.useAndroidX" = "true"
    }

    # JVM 参数单独处理，因为需要判断内存大小
    $jvmArgsKey = "org.gradle.jvmargs"
    $defaultJvmArgs = "-Xmx4g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8 -XX:+UseParallelGC -XX:MaxMetaspaceSize=1g"

    $fileContent = @()
    $existingKeys = @{}

    if (Test-Path $GradlePropsFile) {
        $fileContent = Get-Content $GradlePropsFile
        # 提取现有的 Key (忽略注释和空行)
        foreach ($line in $fileContent) {
            if ($line -match "^\s*([^#][^=]+?)\s*=") {
                $existingKeys[$Matches[1].Trim()] = $true
            }
        }
    } else {
        Write-Warn "未找到 $GradlePropsFile，将自动创建。"
        $fileContent += "# Generated by Android Build Script (PowerShell 7)"
        $fileContent += ""
    }

    $modified = $false

    # 1. 注入常规优化参数
    foreach ($key in $optimizedConfigs.Keys) {
        if (-not $existingKeys.ContainsKey($key)) {
            $fileContent += "$key=$($optimizedConfigs[$key])"
            Write-Success "已添加: $key = $($optimizedConfigs[$key])"
            $modified = $true
        } elseif ($existingKeys.ContainsKey($key)) {
            # 如果存在，检查值是否为 true (简单校验)
            $currentLine = $fileContent | Where-Object { $_ -match "^\s*$key\s*=" }
            if ($currentLine -match "=\s*false") {
                Write-Warn "发现 $key 当前为 false，已强制修正为 true！"
                $fileContent = $fileContent | ForEach-Object { $_ -replace "^\s*$key\s*=\s*false", "$key=true" }
                $modified = $true
            }
        }
    }

    # 2. 注入 JVM 内存参数 (核心防卡顿)
    if (-not $existingKeys.ContainsKey($jvmArgsKey)) {
        $fileContent += "$jvmArgsKey=$defaultJvmArgs"
        Write-Success "已添加 JVM 内存参数: 分配 4G 堆内存并开启 ParallelGC"
        $modified = $true
    } else {
        Write-Info "JVM 参数已存在，保留您的自定义配置 (请确保 -Xmx 至少为 2g)。"
    }

    if ($modified) {
        $fileContent | Set-Content $GradlePropsFile -Encoding UTF8
        Write-Success "性能优化配置已写入 $GradlePropsFile！下次编译将自动起飞。"
    } else {
        Write-Success "您的 $GradlePropsFile 已经是满血高性能状态，无需修改！"
    }
}

# ================= 核心业务函数 =================

function Invoke-DebugBuild {
    Write-Step "开始编译 Debug 版本 (已默认开启 Parallel 并行编译)..."
    # 加上 --parallel 确保即使配置文件没写，命令行也强制并行
    & $GradleWrapper assembleDebug --parallel --warning-mode all
    
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Debug 编译失败！请检查上方错误日志。"
        return
    }
    Write-Success "Debug 编译成功！"

    $apkPath = Get-ChildItem -Path ".\app\build\outputs\apk\debug" -Filter "*.apk" | Select-Object -First 1
    if (-not $apkPath) {
        Write-Err "找不到生成的 Debug APK！"
        return
    }

    Write-Step "正在安装 APK 到设备..."
    $device = Select-TargetDevice
    if (-not $device) { return }

    adb install -r -d $apkPath.FullName
    if ($LASTEXITCODE -ne 0) {
        Write-Err "APK 安装失败！"
        return
    }
    Write-Success "APK 安装成功！"

    $pkgName = Get-AppPackageName
    if ($pkgName) {
        Write-Step "正在启动应用 ($pkgName) 并过滤 Logcat 日志..."
        adb logcat -c
        adb shell monkey -p $pkgName -c android.intent.category.LAUNCHER 1 | Out-Null
        
        Write-Success "应用已启动！正在实时显示日志 (按 Ctrl+C 停止)..."
        Write-Host "---------------------------------------------------" -ForegroundColor DarkGray
        $pid = adb shell pidof $pkgName
        if ($pid) {
            adb logcat --pid=$pid
        } else {
            adb logcat | Select-String -Pattern $pkgName
        }
    } else {
        Write-Warn "未能自动获取包名，跳过自动启动和日志过滤。"
    }
}

function Invoke-ReleaseBuild {
    Write-Step "开始编译 Release 版本 (签名打包)..."
    & $GradleWrapper assembleRelease bundleRelease --parallel
    
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Release 编译失败！"
        return
    }

    Write-Success "Release 构建完成！"
    Write-Step "生成的产物路径："
    
    $apks = Get-ChildItem -Path ".\app\build\outputs\apk\release" -Filter "*.apk" -ErrorAction SilentlyContinue
    $aabs = Get-ChildItem -Path ".\app\build\outputs\bundle\release" -Filter "*.aab" -ErrorAction SilentlyContinue

    if ($apks) {
        Write-Host "  [APK]" -ForegroundColor $ColorHighlight
        $apks | ForEach-Object { Write-Host "   -> $($_.FullName)" -ForegroundColor $ColorSuccess }
    }
    if ($aabs) {
        Write-Host "  [AAB (Google Play 格式)]" -ForegroundColor $ColorHighlight
        $aabs | ForEach-Object { Write-Host "   -> $($_.FullName)" -ForegroundColor $ColorSuccess }
    }
    
    if ($IsWindows -and ($apks -or $aabs)) {
        $folder = if ($apks) { $apks[0].DirectoryName } else { $aabs[0].DirectoryName }
        Write-Step "正在为你打开输出文件夹..."
        Invoke-Item $folder
    }
}

function Invoke-CleanProject {
    Write-Step "正在清理项目 (Clean)..."
    & $GradleWrapper clean
    if ($LASTEXITCODE -eq 0) { Write-Success "项目清理完毕！" } else { Write-Err "清理失败！" }
}

function Invoke-BuildScan {
    Write-Step "正在执行 Build Scan (性能分析)..."
    Write-Warn "提示：编译完成后，终端会提示你同意服务条款 (输入 yes)，然后会生成一个网页链接。"
    Write-Warn "请在浏览器打开该链接，查看 Timeline (甘特图)，验证 Parallel 并行编译是否生效！"
    
    & $GradleWrapper assembleDebug --parallel --scan
}

function Show-LogcatOnly {
    $device = Select-TargetDevice
    if (-not $device) { return }
    
    $pkgName = Get-AppPackageName
    adb logcat -c
    Write-Success "开始监听 Logcat 日志 (按 Ctrl+C 停止)..."
    if ($pkgName) {
        $pid = adb shell pidof $pkgName
        if ($pid) { adb logcat --pid=$pid } else { adb logcat }
    } else {
        adb logcat
    }
}

function Show-InteractiveMenu {
    Clear-Host
    Write-Host "=====================================================" -ForegroundColor $ColorHighlight
    Write-Host "   Android 极速构建终端 (满血性能优化版 PS7)         " -ForegroundColor $ColorHighlight
    Write-Host "=====================================================" -ForegroundColor $ColorHighlight
    Write-Host ""
    Write-Host "  [1] 一键调试 (编译 + 安装 + 启动 + 看日志)" -ForegroundColor $ColorSuccess
    Write-Host "  [2] 编译发布 (打 Release APK/AAB 包)" -ForegroundColor $ColorWarning
    Write-Host "  [3] 🚀 一键性能优化 (自动配置 parallel 等参数)" -ForegroundColor $ColorHighlight
    Write-Host "  [4] 📊 性能分析 (Build Scan 生成甘特图)" -ForegroundColor $ColorInfo
    Write-Host "  [5] 清理项目 (Clean)" -ForegroundColor $ColorInfo
    Write-Host "  [6] 仅看日志 (Logcat)" -ForegroundColor $ColorInfo
    Write-Host "  [7] 查看已连接设备" -ForegroundColor $ColorInfo
    Write-Host "  [0] 退出" -ForegroundColor DarkGray
    Write-Host ""
    
    do {
        $choice = Read-Host "请输入选项 (0-7)"
    } while ($choice -notin '0','1','2','3','4','5','6','7')

    switch ($choice) {
        '1' { Invoke-DebugBuild }
        '2' { Invoke-ReleaseBuild }
        '3' { Optimize-GradleProperties }
        '4' { Invoke-BuildScan }
        '5' { Invoke-CleanProject }
        '6' { Show-LogcatOnly }
        '7' { adb devices }
        '0' { exit }
    }
}

# ================= 脚本执行入口 =================

Test-Environment

switch ($Action) {
    "Menu"     { Show-InteractiveMenu }
    "Debug"    { Invoke-DebugBuild }
    "Release"  { Invoke-ReleaseBuild }
    "Clean"    { Invoke-CleanProject }
    "Logcat"   { Show-LogcatOnly }
    "Devices"  { adb devices }
    "Optimize" { Optimize-GradleProperties }
    "Scan"     { Invoke-BuildScan }
}