param([switch]$DebugBuild,[switch]$SkipChecks)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$taskJdk = Join-Path $projectRoot '.tools\jdk'
if (Test-Path -LiteralPath $taskJdk) { $jdkDir = Get-ChildItem -LiteralPath $taskJdk -Directory | Select-Object -First 1; $env:JAVA_HOME = $jdkDir.FullName }
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw '请先运行 Setup-Android.ps1 或配置 JDK17 的 JAVA_HOME。' }
if (-not $DebugBuild -and -not (Test-Path -LiteralPath (Join-Path $projectRoot 'signing.properties'))) { throw '发布包需要原签名材料。请先还原独立签名备份；新建项目才使用 Generate-Key.ps1。' }
Push-Location -LiteralPath $projectRoot
try {
    $tasks = @()
    if (-not $SkipChecks) { $tasks += 'testDebugUnitTest'; $tasks += 'lintRelease' }
    $tasks += $(if($DebugBuild){'assembleDebug'}else{'assembleRelease'})
    & .\gradlew.bat --no-daemon @tasks
    if ($LASTEXITCODE -ne 0) { throw "构建失败：$LASTEXITCODE" }
    Write-Host ('APK目录：' + (Join-Path $projectRoot $(if($DebugBuild){'app\build\outputs\apk\debug'}else{'app\build\outputs\apk\release'})))
} finally { Pop-Location }
