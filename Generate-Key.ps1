$ErrorActionPreference = 'Stop'
$signingRoot = Join-Path $PSScriptRoot 'private-signing'
if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'signing.properties')) { throw '签名已存在，不会覆盖。后续升级必须沿用原签名。' }
if (-not $env:JAVA_HOME) { throw '请先设置 JDK17 的 JAVA_HOME。' }
New-Item -ItemType Directory -Force -Path $signingRoot | Out-Null
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
$password = -join ($bytes | ForEach-Object {$_.ToString('x2')})
$passFile = Join-Path $signingRoot 'password.txt'
[IO.File]::WriteAllText($passFile,$password,[Text.UTF8Encoding]::new($false))
& (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore (Join-Path $signingRoot 'qingji-release.p12') -storetype PKCS12 -storepass:file $passFile -keypass:file $passFile -alias qingji -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=QingJi Personal Build,OU=Personal,O=QingJi,L=Private,ST=Private,C=CN'
if ($LASTEXITCODE -ne 0) { throw '生成签名失败。' }
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'signing.properties'),"storeFile=private-signing/qingji-release.p12`nstorePassword=$password`nkeyAlias=qingji`nkeyPassword=$password`n",[Text.UTF8Encoding]::new($false))
Write-Host '已生成本项目专用签名。请单独备份 private-signing 和 signing.properties，不要随 APK 分发。'
