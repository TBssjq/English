<#
.SYNOPSIS
    同步修改应用版本号与检查更新数据。

.DESCRIPTION
    修改 app/build.gradle.kts 的 versionCode / versionName，并同步项目与网站
    version 目录下的检查更新文件。只替换版本号字符串，保留 JSON 原有格式，
    不重新序列化整份文件。

.PARAMETER Version
    新的 versionName，例如 2.3 或 2.3.1。

.PARAMETER Code
    新的 versionCode。不传时在当前值上加 1。

.PARAMETER SitePath
    网站仓库本地路径。

.PARAMETER NoSite
    只改项目内文件，不碰网站仓库。

.PARAMETER WhatIf
    预览将要修改的内容，不实际写文件。

.EXAMPLE
    .\bump-version.ps1 -Version 2.3

.EXAMPLE
    .\bump-version.ps1 -Version 2.3 -Code 6 -WhatIf

.EXAMPLE
    .\bump-version.ps1 -Version 2.3 -NoSite
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidatePattern('^\d+\.\d+(\.\d+)?$')]
    [string]$Version,

    [int]$Code,

    [string]$SitePath = "D:\project\TBssjq.github.io-main",

    [switch]$NoSite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    $gradle = Join-Path $root "app/build.gradle.kts"
    if (-not (Test-Path $gradle)) { throw "找不到 app/build.gradle.kts" }

    $gradleText = Get-Content $gradle -Raw
    $codeMatch = [regex]::Match($gradleText, 'versionCode\s*=\s*(\d+)')
    if (-not $codeMatch.Success) { throw "build.gradle.kts 中没有 versionCode" }

    $currentCode = [int]$codeMatch.Groups[1].Value
    $newCode = if ($PSBoundParameters.ContainsKey('Code')) { $Code } else { $currentCode + 1 }
    if ($newCode -lt 1) { throw "versionCode 必须大于 0" }

    $targets = [System.Collections.Generic.List[object]]::new()
    $targets.Add(@{
        Path    = $gradle
        Pattern = '(?m)^((\s*)versionCode\s*=\s*)\d+'
        Replace = '${1}' + $newCode
        Label   = "versionCode $currentCode -> $newCode"
    })
    $targets.Add(@{
        Path    = $gradle
        Pattern = '(?m)^((\s*)versionName\s*=\s*)"[^"]+"'
        Replace = '${1}"' + $Version + '"'
        Label   = "versionName -> $Version"
    })

    $jsonFiles = @(
        @{ Path = (Join-Path $root "english.json"); Key = "latestVersion" },
        @{ Path = (Join-Path $root "log.json"); Key = "latest" }
    )
    if (-not $NoSite) {
        $jsonFiles += @{ Path = (Join-Path $SitePath "version\english.json"); Key = "latestVersion" }
        $jsonFiles += @{ Path = (Join-Path $SitePath "version\english.log.json"); Key = "latest" }
    }

    foreach ($item in $jsonFiles) {
        if (-not (Test-Path $item.Path)) { throw "找不到文件: $($item.Path)" }
        try {
            Get-Content $item.Path -Raw | ConvertFrom-Json | Out-Null
        } catch {
            throw "JSON 解析失败: $($item.Path) — $($_.Exception.Message)"
        }
        $targets.Add(@{
            Path    = $item.Path
            Pattern = '("' + [regex]::Escape($item.Key) + '"\s*:\s*")[^"]+(")'
            Replace = '${1}' + $Version + '${2}'
            Label   = "$($item.Key) -> $Version"
        })
    }

    $didWrite = $false
    foreach ($target in $targets) {
        $text = Get-Content $target.Path -Raw
        if ($text -notmatch $target.Pattern) {
            Write-Warning "未匹配，跳过: $($target.Path) [$($target.Label)]"
            continue
        }
        $newText = [regex]::Replace($text, $target.Pattern, $target.Replace)
        if ($PSCmdlet.ShouldProcess($target.Path, $target.Label)) {
            # 统一写成 UTF-8 无 BOM，避免 Gradle 与 JSON 解析出现编码差异
            [System.IO.File]::WriteAllText(
                $target.Path,
                $newText,
                [System.Text.UTF8Encoding]::new($false)
            )
            $didWrite = $true
        }
        Write-Host ("  {0}: {1}" -f (Split-Path $target.Path -Leaf), $target.Label)
    }

    if ($didWrite) {
        foreach ($item in $jsonFiles) {
            try {
                Get-Content $item.Path -Raw | ConvertFrom-Json | Out-Null
            } catch {
                throw "写入后 JSON 解析失败: $($item.Path)"
            }
        }
        Write-Host "`n完成。记得更新 log.json 的条目与 english.log.json 的公告正文，再构建与提交。"
    }
} finally {
    Pop-Location
}
