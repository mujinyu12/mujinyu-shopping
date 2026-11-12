# 慕今予商城 · 后端

一套基于 **Spring Cloud Alibaba + Apache Dubbo** 的分布式电商后端，24 个 Maven 模块，覆盖商品、分类、搜索、购物车、订单、支付、秒杀、用户、客服（AI）、文件、邮件、短信等完整链路。

> 配套前端：[shopping-user](https://github.com/mujinyu12/shopping-user)（用户端） · [shopping-admin](https://github.com/mujinyu12/shopping-admin)（管理端）

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言 / 构建 | Java 17、Maven 3.9 |
| 基础框架 | Spring Boot 3.5.4、Spring Cloud 2025.0.0 |
| 微服务治理 | Spring Cloud Alibaba 2025.0.0.0（Nacos 注册与配置中心）、Apache Dubbo 3.3.0（RPC） |
| 持久层 | MyBatis-Plus 3.5.12、MySQL 8.0、Druid |
| 缓存 | Redis（Jedis） |
| 搜索 | Elasticsearch 8.10.4（spring-data-elasticsearch） |
| 消息 | RocketMQ（商品增删同步 ES、购物车同步） |
| 对象存储 | MinIO |
| 限流熔断 | Sentinel |
| 链路追踪 | SkyWalking |
| 认证 | JWT（jose4j）+ 自定义参数解析器 |
| 支付 | 微信支付 API v3 |
| AI 客服 | Spring AI（函数调用 + FAQ 检索） |

## 架构

```
                       ┌──────────────────────────┐
   浏览器 ───────────►  │  shopping-user / -admin  │  Vue 3 + Vite
                       └───────────┬──────────────┘
                                   │ HTTP (Vite dev proxy → localhost:800x)
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          ▼                          ▼
┌───────────────┐          ┌───────────────┐          ┌───────────────┐
│  manager_api  │          │  *_customer_api│          │   webhooks    │
│     :8001     │          │    :8002~8008 │          │     :8100     │
└───────┬───────┘          └───────┬───────┘          └───────────────┘
        │        Dubbo RPC（Nacos 服务发现）      │
        └──────────────────┬─────────────────────┘
                           ▼
   ┌───────────────────────────────────────────────────────────┐
   │  *_service  :20880 ~ :20892   （业务提供者，只暴露 Dubbo 接口）│
   └───────────────────────────────────────────────────────────┘
        │            │             │            │           │
        ▼            ▼             ▼            ▼           ▼
     MySQL         Redis           ES        RocketMQ     MinIO
   baizhanshopping              goods 索引
```

分三层，职责清晰：

- **`*_customer_api`**：面向前端用户端的 HTTP 接口层，薄，只做参数校验与转发
- **`*_service`**：Dubbo 服务提供者，业务逻辑与数据访问都在这里
- **`manager_api`**：管理后台的 HTTP 接口层，聚合调用各 `*_service`

## 模块与端口

### 接口层（HTTP）

| 模块 | 端口 | 说明 |
| --- | --- | --- |
| `shopping_manager_api` | 8001 | 管理端后台接口 |
| `shopping_category_customer_api` | 8002 | 分类/品牌查询 |
| `shopping_user_customer_api` | 8003 | 用户注册登录、收货地址 |
| `shopping_search_customer_api` | 8004 | 商品搜索、搜索建议 |
| `shopping_cart_customer_api` | 8005 | 购物车 |
| `shopping_order_customer_api` | 8006 | 订单、支付 |
| `shopping_seckill_customer_api` | 8007 | 秒杀 |
| `shopping_custcare_customer_api` | 8008 | AI 客服 |
| `shopping_webhooks` | 8100 | SkyWalking 告警回调 |

### 服务层（Dubbo）

| 模块 | Dubbo 端口 | 说明 |
| --- | --- | --- |
| `shopping_goods_service` | 20880 | 商品（增删改查、同步 ES） |
| `shopping_admin_service` | 20881 | 管理员、角色、权限 |
| `shopping_category_service` | 20882 | 分类、品牌 |
| `shopping_file_service` | 20883 | MinIO 文件上传 |
| `shopping_cart_service` | 20884 | 购物车 |
| `shopping_mail_service` | 20885 | 邮件 |
| `shopping_sms_service` | 20886 | 短信 |
| `shopping_search_service` | 20887 | ES 检索、商品索引维护 |
| `shopping_user_service` | 20888 | 用户、JWT 签发 |
| `shopping_order_service` | 20889 | 订单、退款 |
| `shopping_pay_service` | 20890 | 微信支付 v3 |
| `shopping_seckill_service` | 20891 | 秒杀（Redis + Lua 扣减库存） |
| `shopping_custcare_service` | 20892 | AI 客服（Spring AI + FAQ + 订单查询函数调用） |
| `shopping_common` | — | 公共模块：统一响应、异常、鉴权、工具类 |

> 接口层的 Dubbo QoS 端口在 8719~8726，属于 Dubbo 自带，正常运行时可见。

## 中间件

| 中间件 | 地址（开发环境） | 用途 |
| --- | --- | --- |
| Nacos | `192.168.66.100:8848` | 注册中心 + 配置中心（namespace `8ae3393c-2080-4209-87a9-b3a5a948d68f`） |
| MySQL | `127.0.0.1:3306` | 业务库 `baizhanshopping`，表前缀 `bz_` |
| Redis | `192.168.66.100:6379` | 缓存、秒杀库存、订单临时态 |
| Elasticsearch | `192.168.66.100:9200` | 商品索引 `goods` |
| RocketMQ | `192.168.66.100:9876` | 商品/购物车事件 |
| MinIO | `http://192.168.66.100:9100` | 图片存储，bucket `shopping` |
| SkyWalking | — | 链路追踪（agent 未入库，按需下载） |

## 快速开始

### 1. 环境要求

- JDK 17+（本项目在 JDK 21 上验证）
- Maven 3.9+
- 能访问上述中间件

### 2. 配置

**所有配置都在 Nacos，不在仓库里**。每个模块的 `src/main/resources/application.yml` 只保存注册中心地址与 `spring.config.import`，业务配置（数据源、Redis、Dubbo、第三方密钥）全部从 Nacos 拉取：

```yaml
spring:
  config:
    import: optional:nacos:shopping_goods_service-dev.yaml
```

需要导入的配置项命名规则为 `{模块名}-dev.yaml`，例如：

```
shopping_manager_api-dev.yaml
shopping_goods_service-dev.yaml
shopping_order_service-dev.yaml
shopping_pay_service-dev.yaml
...
```

> ⚠️ 密钥（数据库密码、Redis 密码、微信商户号与私钥、MinIO 账号）只在 Nacos 与本机 `~/.shopping/wxpay/apiclient_key.pem`，**没有**进入本仓库。

### 3. 数据库

在 MySQL 中创建库 `baizhanshopping`（utf8mb4），并导入表结构与初始数据（26 张表，前缀 `bz_`）。表结构不在仓库内，可从你本机导出：

```bash
mysqldump --no-data -h127.0.0.1 -uroot -p baizhanshopping > schema.sql
mysqldump --no-create-info --complete-insert -h127.0.0.1 -uroot -p baizhanshopping > seed.sql
```

### 4. 启动顺序

```bash
# 1) 先编译公共模块与全部服务
mvn -DskipTests clean install

# 2) 启动注册/配置中心依赖的中间件（Nacos、MySQL、Redis、ES、RocketMQ、MinIO）

# 3) 启动 Dubbo 服务提供者 —— 每个模块一个终端，spring-boot:run 会阻塞
mvn -pl shopping_admin_service spring-boot:run
mvn -pl shopping_goods_service spring-boot:run
# ... 其余 *_service 同理

# 4) 启动 HTTP 接口层
mvn -pl shopping_manager_api spring-boot:run
```

> `spring-boot:run` 是前台阻塞的，一条命令只能起一个模块。要一次起多个，最省事的是在 IDEA 里给每个 `*Application` 建运行配置（或装一个 Run Configuration 组），一键批量启动 —— 20 多个服务逐个敲命令不现实。

### 5. 验证

```bash
# 管理端登录（账号在 bz_admin 表，密码为 md5(明文 + "MUJINYU")）
curl -X POST http://localhost:8001/admin/login \
     -d "username=<你的管理员账号>&password=<明文密码>"

# 商品搜索
curl -X POST http://localhost:8004/user/goodsSearch/search \
     -H "Content-Type: application/json" \
     -d '{"keyword":"手机","page":1,"size":5}'
```

## 关键设计说明

- **秒杀**：`Redis + Lua` 原子扣减库存，下单只写 Redis（带 5 分钟过期），支付成功才落库到 `bz_orders` 并回补/确认库存
- **搜索**：商品写操作通过 RocketMQ 通知 `search_service` 同步 ES；删除走 `del_goods_queue`
- **鉴权**：登录返回 JWT，前端放在 `Authorization: Bearer <token>`；`LoginUserArgumentResolver` 解析后注入 `LoginUser`
- **支付**：普通订单走微信 Native 扫码；秒杀订单走模拟支付接口（演示用，不调微信）
- **AI 客服**：系统提示词在 Nacos 配置 `custcare.system-prompt`，支持订单查询等函数调用与 FAQ 语义检索

## 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| `No provider available from registry ... for service XxxService` | 对应的 `*_service` 没启动，或 Nacos 里没有它的 `providers:...` 记录 |
| 前端 500，后端日志 `接口不存在` | 订单不存在时抛的是业务异常码 404；秒杀订单支付前只在 Redis |
| 商品列表比数据库多几条 | ES 有残留文档，删除商品时 MQ 掉线会导致不同步，可 `DELETE /goods/_doc/{id}` 后 `POST /goods/_refresh` |
| 搜索报 `cluster_block_exception` / 503 | ES 集群重启恢复中，或磁盘超过 90% 水位触发只读块 |
| 时间差 8 小时 | `@JsonFormat` 未指定 `timezone`，Jackson 默认按 UTC 解析；本项目已在 `SeckillGoods` 上固定 `GMT+8` |

## 目录结构

```
shopping/
├── pom.xml                     # 父 POM，统一版本管理
├── shopping_common/            # 公共模块（响应体、异常、鉴权、工具）
├── shopping_*_service/         # Dubbo 服务提供者
├── shopping_*_customer_api/    # 用户端 HTTP 接口
├── shopping_manager_api/       # 管理端 HTTP 接口
└── skywalking-agent/           # 本地链路追踪 agent（未入库）
```

## 声明

个人学习 / 毕业设计项目，用于演示分布式电商的常见架构。代码可自由参考，第三方 SDK 与素材版权归各自所有者。