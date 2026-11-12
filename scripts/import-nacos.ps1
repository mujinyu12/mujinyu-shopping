<#
.SYNOPSIS
    把 deploy\nacos\*.yaml 发布到 Nacos 配置中心。

.DESCRIPTION
    模板里的 __XXX__ 占位符会按本脚本的参数替换后再上传，Nacos 里存的是最终值，
    因此不会把密钥写进仓库。发布前请先确认 Nacos 中已存在目标命名空间。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\import-nacos.ps1 `
        -Nacos http://127.0.0.1:8848 `
        -Namespace public `
        -InfraHost 192.168.1.10 `
        -DubboHost 192.168.1.10 `
        -MysqlPassword 你的数据库密码

.EXAMPLE
    # 只看替换结果，不上传
    powershell -ExecutionPolicy Bypass -File scripts\import-nacos.ps1 -DryRun -InfraHost 10.0.0.5
#>
param(
    [string]$Nacos = "http://127.0.0.1:8848",
    [string]$User = "nacos",
    [string]$Password = "nacos",
    [string]$Namespace = "public",

    [string]$InfraHost = "127.0.0.1",
    [string]$DubboHost = "127.0.0.1",
    [string]$MysqlHost = "127.0.0.1",
    [string]$MysqlPassword = "123456",

    [string]$NacosPassword = "",
    [string]$MinioAccessKey = "minioadmin",
    [string]$MinioSecretKey = "minioadmin",
    [string]$MailUser = "your@example.com",
    [string]$MailPassword = "",
    [string]$WxpayMchId = "",
    [string]$WxpayMchSerialNo = "",
    [string]$WxpayApiV3Key = "",
    [string]$WxpayAppid = "",
    [string]$WxpayNotifyDomain = "https://example.com",

    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$utf8 = New-Object System.Text.UTF8Encoding($false)

# 应用自身访问 Nacos 时用的密码，默认与登录密码一致
if ([string]::IsNullOrEmpty($NacosPassword)) { $NacosPassword = $Password }

$root = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $root "deploy\nacos"
if (-not (Test-Path $dir)) { throw "找不到配置模板目录：$dir" }
$files = Get-ChildItem $dir -Filter *.yaml | Sort-Object Name
if ($files.Count -eq 0) { throw "$dir 下没有 yaml 模板" }

$map = [ordered]@{
    "__INFRA_HOST__"          = $InfraHost
    "__DUBBO_HOST__"          = $DubboHost
    "__MYSQL_HOST__"          = $MysqlHost
    "__MYSQL_PASSWORD__"      = $MysqlPassword
    "__NACOS_PASSWORD__"      = $NacosPassword
    "__NACOS_NAMESPACE__"     = $Namespace
    "__MINIO_ACCESS_KEY__"    = $MinioAccessKey
    "__MINIO_SECRET_KEY__"    = $MinioSecretKey
    "__MAIL_USER__"           = $MailUser
    "__MAIL_PASSWORD__"       = $MailPassword
    "__WXPAY_MCH_ID__"        = $WxpayMchId
    "__WXPAY_MCH_SERIAL_NO__" = $WxpayMchSerialNo
    "__WXPAY_API_V3_KEY__"    = $WxpayApiV3Key
    "__WXPAY_APPID__"         = $WxpayAppid
    "__WXPAY_NOTIFY_DOMAIN__" = $WxpayNotifyDomain
}

function Convert-Template([string]$text) {
    foreach ($k in $map.Keys) { $text = $text.Replace($k, [string]$map[$k]) }
    return $text
}

Write-Host "模板目录：$dir"
Write-Host "目标 Nacos：$Nacos  命名空间：$Namespace"

if ($DryRun) {
    $sample = Convert-Template ([System.IO.File]::ReadAllText($files[0].FullName, $utf8))
    Write-Host "`n[DryRun] 不写入 Nacos。$($files[0].Name) 替换后前 20 行：`n"
    ($sample -split "`n" | Select-Object -First 20) | ForEach-Object { Write-Host "  $_" }
    Write-Host "`n[DryRun] 共 $($files.Count) 个模板待发布。"
    return
}

# 1) 登录拿 accessToken
$loginBody = "username=$([uri]::EscapeDataString($User))&password=$([uri]::EscapeDataString($Password))"
$login = Invoke-RestMethod -Uri "$Nacos/nacos/v1/auth/login" -Method Post `
    -Body $utf8.GetBytes($loginBody) -ContentType "application/x-www-form-urlencoded"
$token = $login.accessToken
if ([string]::IsNullOrEmpty($token)) { throw "登录 Nacos 失败：未返回 accessToken（请检查账号密码或服务端是否开启鉴权）" }
Write-Host "登录成功，开始发布 $($files.Count) 个配置`n"

# 2) 逐个发布
$ok = 0
$failed = @()
foreach ($f in $files) {
    $content = Convert-Template ([System.IO.File]::ReadAllText($f.FullName, $utf8))
    $form = "dataId=$([uri]::EscapeDataString($f.Name))" +
            "&groupName=DEFAULT_GROUP" +
            "&namespaceId=$([uri]::EscapeDataString($Namespace))" +
            "&type=yaml" +
            "&content=$([uri]::EscapeDataString($content))" +
            "&accessToken=$([uri]::EscapeDataString($token))"
    try {
        $r = Invoke-RestMethod -Uri "$Nacos/nacos/v3/admin/cs/config" -Method Post `
            -Body $utf8.GetBytes($form) -ContentType "application/x-www-form-urlencoded; charset=utf-8"
        if ($r.code -eq 0) {
            $ok++
            Write-Host ("  [OK]   {0}  ({1} 字符)" -f $f.Name, $content.Length)
        } else {
            $failed += "$($f.Name): $($r.message)"
        }
    } catch {
        $failed += "$($f.Name): $($_.Exception.Message)"
    }
}

Write-Host "`n完成：成功 $ok 个，失败 $($failed.Count) 个"
if ($failed.Count -gt 0) {
    $failed | ForEach-Object { Write-Host "  [FAIL] $_" }
    exit 1
}