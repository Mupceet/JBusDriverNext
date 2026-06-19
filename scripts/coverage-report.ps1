#requires -Version 5.1
<#
.SYNOPSIS
    解析 JaCoCo 单元测试覆盖率报告 (report.xml)，输出全局/包/类三层覆盖率。

.DESCRIPTION
    AGP 开启 enableUnitTestCoverage 后，运行
    ./gradlew createDebugUnitTestCoverageReport 生成 report.xml。
    本脚本解析该 XML，按包和源文件聚合并计算行覆盖率。

.PARAMETER ReportPath
    report.xml 路径，默认从脚本位置推导：
    <repo>/app/build/reports/coverage/test/debug/report.xml

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/coverage-report.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/coverage-report.ps1 -ReportPath path\to\report.xml
#>
param(
    [string]$ReportPath = ""
)

$ErrorActionPreference = 'Stop'

if (-not $ReportPath) {
    $repoRoot = Split-Path $PSScriptRoot -Parent
    $ReportPath = Join-Path $repoRoot 'app\build\reports\coverage\test\debug\report.xml'
}

if (-not (Test-Path -LiteralPath $ReportPath)) {
    Write-Error "Coverage report not found: $ReportPath`nRun './gradlew createDebugUnitTestCoverageReport' first."
    exit 1
}

[xml]$r = Get-Content -Raw -LiteralPath $ReportPath

function Pct($m, $c) {
    $tot = [int]$m + [int]$c
    if ($tot -gt 0) { '{0:N1}' -f (100.0 * [int]$c / $tot) } else { '0.0' }
}

Write-Output "###GLOBAL###  (type missed covered pct)"
foreach ($ct in $r.report.counter) {
    Write-Output ("G {0} {1} {2} {3}" -f $ct.type, $ct.missed, $ct.covered, (Pct $ct.missed $ct.covered))
}

Write-Output "###PKG###  (pct missed covered pkg)"
foreach ($pk in $r.report.package) {
    $line = $pk.counter | Where-Object { $_.type -eq 'LINE' }
    if ($line) {
        Write-Output ("P {0} {1} {2} {3}" -f (Pct $line.missed $line.covered), $line.missed, $line.covered, $pk.name)
    }
}

Write-Output "###CLASS###  (missed covered pct file)"
foreach ($pk in $r.report.package) {
    foreach ($cl in $pk.class) {
        $line = $cl.counter | Where-Object { $_.type -eq 'LINE' }
        if ($line) {
            Write-Output ("C {0} {1} {2} {3}" -f $line.missed, $line.covered, (Pct $line.missed $line.covered), $cl.sourcefilename)
        }
    }
}
