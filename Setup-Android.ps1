$ErrorActionPreference = 'Stop'
$toolRoot = Join-Path $PSScriptRoot '.tools'
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
function Fetch-Zip([string]$Uri,[string]$Zip,[string]$Destination) {
    if (-not (Test-Path -LiteralPath $Zip)) { Invoke-WebRequest -Uri $Uri -OutFile $Zip }
    if (-not (Test-Path -LiteralPath $Destination)) { Expand-Archive -LiteralPath $Zip -DestinationPath $Destination }
}
Fetch-Zip 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' (Join-Path $toolRoot 'jdk17.zip') (Join-Path $toolRoot 'jdk')
$taskJdk = Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1
$env:JAVA_HOME = $taskJdk.FullName
Fetch-Zip 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' (Join-Path $toolRoot 'sdk-cli.zip') (Join-Path $toolRoot 'sdk-cli')
$sdkRoot = Join-Path $toolRoot 'sdk'
$latest = Join-Path $sdkRoot 'cmdline-tools\latest'
if (-not (Test-Path -LiteralPath $latest)) {
    New-Item -ItemType Directory -Force -Path (Join-Path $sdkRoot 'cmdline-tools') | Out-Null
    Copy-Item -LiteralPath (Join-Path $toolRoot 'sdk-cli\cmdline-tools') -Destination $latest -Recurse
}
$env:ANDROID_HOME = $sdkRoot
$manager = Join-Path $latest 'bin\sdkmanager.bat'
Write-Host '准备安装安卓开发组件并接受 Android SDK 许可。'
(1..100 | ForEach-Object {'y'}) | & $manager "--sdk_root=$sdkRoot" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' 'build-tools;34.0.0'
if ($LASTEXITCODE -ne 0) { throw 'SDK安装失败，请检查网络和终端输出。' }
$value = $sdkRoot.Replace('\','/')
$escaped = -join ($value.ToCharArray() | ForEach-Object { if ([int]$_ -gt 127) { '\u' + ([int]$_).ToString('x4') } else { [string]$_ } })
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'local.properties'),"sdk.dir=$escaped`n",[Text.UTF8Encoding]::new($false))
Write-Host '安卓环境已准备好。接下来运行 .\Build.ps1 -DebugBuild 或还原签名后运行 .\Build.ps1。'
