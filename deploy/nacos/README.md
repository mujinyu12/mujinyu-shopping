# Nacos 配置模板

这里放的是 22 个模块在 Nacos 里的配置骨架（dataId 形如 `{模块名}-dev.yaml`）。

因为配置里包含数据库密码、MinIO 密钥、微信支付商户参数，仓库只保存**带占位符的模板**，真实值在你导入时注入，不会进入 Git 历史。

## 怎么用

### 方式一：脚本导入（推荐）

```powershell
powershell -ExecutionPolicy Bypass -File scripts\import-nacos.ps1 `
    -Nacos      http://127.0.0.1:8848 `
    -Namespace  你的命名空间ID `
    -InfraHost  10.0.0.5 `
    -DubboHost  10.0.0.5 `
    -MysqlPassword 你的数据库密码 `
    -MinioAccessKey minioadmin -MinioSecretKey minioadmin
```

脚本会登录 Nacos、替换占位符、逐个发布，并打印每个 dataId 的结果。先看替换结果可以加 `-DryRun`。

### 方式二：控制台手工新建

打开 Nacos 控制台 → 配置管理 → 新建配置，dataId 填文件名，group 选 `DEFAULT_GROUP`，格式选 YAML，然后把对应文件内容粘进去（记得先把占位符换成真实值）。

## 占位符对照

| 占位符 | 含义 | 脚本参数 | 默认值 |
| --- | --- | --- | --- |
| `__INFRA_HOST__` | 中间件所在主机（Nacos/Redis/ES/RocketMQ/MinIO 共用） | `-InfraHost` | `127.0.0.1` |
| `__DUBBO_HOST__` | Dubbo 注册到 Nacos 的本机 IP，多网卡时务必显式指定 | `-DubboHost` | `127.0.0.1` |
| `__MYSQL_HOST__` | MySQL 地址 | `-MysqlHost` | `127.0.0.1` |
| `__MYSQL_PASSWORD__` | MySQL root 密码 | `-MysqlPassword` | `123456` |
| `__NACOS_PASSWORD__` | 服务访问 Nacos 用的密码 | `-NacosPassword` | 与 `-Password` 相同 |
| `__NACOS_NAMESPACE__` | 配置和注册所在的命名空间 ID | `-Namespace` | `public` |
| `__MINIO_ACCESS_KEY__` / `__MINIO_SECRET_KEY__` | MinIO 账号 | `-MinioAccessKey` / `-MinioSecretKey` | `minioadmin` |
| `__MAIL_USER__` / `__MAIL_PASSWORD__` | 发件邮箱与 SMTP 授权码 | `-MailUser` / `-MailPassword` | `your@example.com` / 空 |
| `__WXPAY_MCH_ID__` | 微信商户号 | `-WxpayMchId` | 空 |
| `__WXPAY_MCH_SERIAL_NO__` | 商户 API 证书序列号 | `-WxpayMchSerialNo` | 空 |
| `__WXPAY_API_V3_KEY__` | APIv3 密钥 | `-WxpayApiV3Key` | 空 |
| `__WXPAY_APPID__` | 公众号 / 小程序 appid | `-WxpayAppid` | 空 |
| `__WXPAY_NOTIFY_DOMAIN__` | 支付回调域名（需公网可达） | `-WxpayNotifyDomain` | `https://example.com` |

另外 `shopping_custcare_service-dev.yaml` 里的 `api-key: ${DASHSCOPE_API_KEY:}` 是 Spring 占位符，不走脚本替换，启动服务时用环境变量 `DASHSCOPE_API_KEY` 提供即可。

## 微信支付私钥

`wxpay.private-key-path` 指向 `${user.home}/.shopping/wxpay/apiclient_key.pem`，这个文件属于商户证书，需要自己从微信商户平台下载后放到该路径，仓库不提供。

## 说明

- 模板里的 `server.port` 是各模块真实的 HTTP / Dubbo 端口，和主 README 的端口表一致
- `dubbo.protocol.port: -1` 表示由框架自动分配；实际运行端口见主 README
- 导入脚本按 Nacos 3.x 的管理接口实现（`/nacos/v3/admin/cs/config`），登录仍走 `/nacos/v1/auth/login`