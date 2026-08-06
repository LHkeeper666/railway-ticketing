# 铁路售票系统架构设计文档

## 1. 概述

### 1.1 项目定位

铁路售票系统后端服务，提供余票查询、订单创建与支付、用户认证等核心功能，采用 Spring Boot 3 + MyBatis-Plus + Redis + MySQL 技术栈。

### 1.2 技术选型

| 技术 | 版本 | 用途 |
|------|------|------|
| JDK | 17 | 运行环境 |
| Spring Boot | 3.5.13 | 应用框架 |
| MyBatis-Plus | 3.5.10 (spring-boot3-starter) | ORM |
| MySQL | 5.7 | 持久化存储 |
| Redis | latest (Docker) | 缓存 / 分布式锁 |
| Spring Data Redis | (Lettuce) | Redis 客户端 |
| jjwt | 0.12.6 (HS256) | JWT 认证 |
| Spring Security Crypto | — | 密码加密 (BCrypt) |
| FastJSON2 | 2.0.36 | JSON 序列化 |
| Lombok | — | 代码简化 |

### 1.3 部署架构

```
┌─────────────────────────────────────┐
│           Spring Boot App           │
│  (JDK 17, Maven, 内嵌 Tomcat)        │
└──────────┬──────────────────────────┘
           │
    ┌──────┴──────┐
    │             │
┌───▼───┐   ┌────▼────┐
│ MySQL │   │  Redis   │
│  5.7  │   │ (Docker) │
└───────┘   └─────────┘
```

依赖服务通过 Docker Compose 一键启动（`docker-env/docker-compose.yml`），包含 MySQL 5.7 和 Redis。

---

## 2. 分层架构

```
Controller  ──  接收 HTTP 请求，参数绑定，返回 Result<T>
   │
   ▼
Service     ──  业务逻辑编排，事务管理
   │
   ├── Chain (责任链) ── 参数校验、业务校验
   │
   ├── SeatSelector   ── 选座逻辑
   │
   └── Mapper         ── 数据持久化
```

### 2.1 严格分层

- **Controller 层**: 仅负责接收请求、调用 Service、返回 `Result<T>`，不写任何业务逻辑。
- **Service 层**: 业务逻辑编排，`@Transactional` 事务控制。不写内联参数校验（交给责任链）。
- **Mapper 层**: MyBatis-Plus `BaseMapper<T>`，XML 自定义 SQL。

### 2.2 数据流向

```
HTTP Request
  → JwtInterceptor (提取用户信息 → UserContext ThreadLocal)
  → Controller
  → ChainContext.handler(ChainMark, RequestDTO)    // 责任链参数校验
  → Service (业务逻辑)
  → Mapper (数据访问)
  → Result<T> (统一响应)
```

---

## 3. 核心模块设计

### 3.1 责任链校验框架

将前端请求的参数校验从 Service 层剥离，统一放到责任链中处理。

#### 类结构

```
AbstractChainFilter<T>          — 顶层接口，继承 Ordered
  ├── handler(T)                — 校验逻辑
  ├── mark()                    — 返回所属责任链标识
  └── getOrder()                — 执行优先级

AbstractChainContext<T>         — 运行时容器，实现 CommandLineRunner
  └── handler(mark, T)          — 按 mark 分组、按 order 排序后执行

分组标记接口:
  ├── OrderCreateChainFilter    — 下单校验链
  ├── TicketQueryChainFilter    — 余票查询校验链
  ├── OrderPayChainFilter       — 支付校验链
  ├── OrderCancelChainFilter    — 取消订单校验链
  ├── PayNotifyChainFilter      — 支付回调校验链
  ├── AuthLoginChainFilter      — 登录校验链
  ├── AuthRegisterChainFilter   — 注册校验链
  ├── WaitlistCreateChainFilter — 候补创建校验链
  ├── OrderRefundChainFilter    — 退票校验链
  ├── OrderChangeChainFilter    — 改签校验链
  ├── PassengerCreateChainFilter — 乘车人创建校验链
  ├── PassengerUpdateChainFilter — 乘车人更新校验链
  ├── PassengerDeleteChainFilter — 乘车人删除校验链
  ├── UserUpdateChainFilter     — 用户资料修改校验链
  ├── ChangePasswordChainFilter — 修改密码校验链
  ├── UserDeleteChainFilter     — 注销账号校验链
  └── OrderListChainFilter      — 订单列表查询校验链
```

#### 注册机制

`AbstractChainContext` 实现 `CommandLineRunner`，应用启动时自动扫描所有 `AbstractChainFilter` Bean，按 `mark()` 分组、按 `getOrder()` 升序排列。新增大类业务只需：

1. 定义标记接口（extends 对应分组接口，已内置 `default mark()`）
2. 编写 Handler 实现类，加 `@Component`
3. 无需修改容器代码

#### 现有校验链

| 标记 (ChainMarkEnum) | Handler | 职责 |
|---|---|---|
| `ORDER_CREATE` | `OrderCreateParamNotNullChainHandler` | 参数非空校验 |
| `ORDER_CREATE` | `OrderCreateParamVerifyChainHandler` | 参数合法性校验（日期格式等） |
| `ORDER_CREATE` | `OrderCreateStockChainHandler` | 库存预校验 |
| `TICKET_QUERY` | `TicketQueryParamNotNullChainHandler` | 参数非空校验 |
| `TICKET_QUERY` | `TicketQueryParamVerifyChainHandler` | 日期范围校验（≤15天） |
| `ORDER_PAY` | `OrderPayParamNotNullChainHandler` | orderSn 非空 |
| `ORDER_PAY` | `OrderPayParamValidateChainHandler` | 订单状态校验 |
| `ORDER_CANCEL` | `OrderCancelParamNotNullChainHandler` | orderSn 非空 |
| `PAY_NOTIFY` | `PayNotifyParamNotNullChainHandler` | 回调参数非空校验 |
| `PAY_NOTIFY` | `PayNotifySignVerifyChainHandler` | 回调签名校验（order=3，按渠道路由策略验签） |
| `AUTH_LOGIN` | `AuthLoginParamNotNullChainHandler` | 手机号/密码非空 |
| `AUTH_REGISTER` | `AuthRegisterParamNotNullChainHandler` | 注册参数非空 |
| `WAITLIST_CREATE` | `WaitlistCreateParamNotNullChainHandler` | 候补参数非空校验 |
| `WAITLIST_CREATE` | `WaitlistCreateParamVerifyChainHandler` | 候补业务校验（无重复候补、余票为0） |
| `ORDER_REFUND` | `OrderRefundParamNotNullChainHandler` | 订单号+退票ticketId列表非空 |
| `ORDER_REFUND` | `OrderRefundStatusChainHandler` | 订单须为PAID、用户归属校验 |
| `ORDER_CHANGE` | `OrderChangeParamNotNullChainHandler` | 订单号+新车次信息非空 |
| `ORDER_CHANGE` | `OrderChangeStatusChainHandler` | 订单须为PAID、用户归属校验 |
| `USER_UPDATE` | `UserUpdateParamNotNullChainHandler` | 更新资料请求参数非空校验 |
| `CHANGE_PASSWORD` | `ChangePasswordParamNotNullChainHandler` | 旧密码/新密码/确认密码非空 + 新密码一致性校验 |
| `USER_DELETE` | `UserDeleteParamNotNullChainHandler` | 注销密码非空校验 |
| `ORDER_LIST` | `OrderListParamVerifyChainHandler` | status 枚举范围/分页 size≤50 校验 |

---

### 3.2 认证与授权

#### 认证流程

```
POST /auth/login
  → AuthController.login(LoginReqDTO)
  → ChainContext.handler(AUTH_LOGIN, reqDTO)    // 参数非空校验
  → AuthServiceImpl:
      1. 查 User 表 (phone 匹配)
      2. DelegatingPasswordEncoder.matches()  // {bcrypt} / {noop}
      3. JwtUtil.generateToken(userId, username, phone)
  → LoginRespDTO { token, userId, username, phone }
```

```
POST /auth/register
  → ChainContext.handler(AUTH_REGISTER, reqDTO)
  → 查重 phone → DelegatingPasswordEncoder.encode() → insert
```

#### 授权流程

```
请求 Authorization: Bearer <token>
  → JwtInterceptor.preHandle()
    1. 提取 Bearer token
    2. JwtUtil.parseToken()  → Claims
    3. 构建 UserInfo { userId, username, phone }
    4. UserContext.set(userInfo)
  → 业务代码通过 UserContext.get() 获取当前用户
  → JwtInterceptor.afterCompletion() → UserContext.clear()
```

**拦截路径**: `/**` (**排除** `/auth/login`, `/auth/register`, `/ticket/**`, `/order/pay/notify`, `/mock-pay/**`)

**JWT 配置**: HS256 签名，密钥和过期时间通过 `jwt.secret` / `jwt.expiration` 配置（默认 86400s）。

#### 用户中心

在认证基础上，提供个人信息管理功能：

```
GET /user/me
  → UserController.profile()
  → UserContext.get().getUserId()
  → userMapper.selectById(userId)
  → 身份证号 AES 解密 → 脱敏(前3后4)
  → 手机号脱敏(前3后4)
  → UserRespDTO (不含 password)

PUT /user/update
  → Chain: UserUpdateChainFilter (参数非空)
  → BeanUtils.copyProperties + idCard 加密
  → userMapper.updateById()

POST /user/change-password
  → Chain: ChangePasswordChainFilter (旧密码/新密码/确认密码非空 + 一致性)
  → 校验旧密码: passwordEncoder.matches(oldPwd, stored)
  → 新密码 BCrypt 编码后更新

POST /user/delete
  → Chain: UserDeleteChainFilter (密码非空)
  → 校验密码确认身份
  → LambdaUpdateWrapper 显式设 delFlag=1 + deletionTime
  → 注意: @TableLogic + updateById 存在兼容问题，改用 Wrappers.lambdaUpdate()
```

**身份证加密**: 使用 JDK 自带 `javax.crypto.Cipher`（AES/CBC/PKCS5Padding），密钥通过 `aes.secret-key` 配置注入。解密失败时自动降级返回原文（兼容存量明文数据）。

---

### 3.3 缓存体系

#### 缓存层次

| 缓存 Key 模板 | 内容 | TTL | 用途 |
|---|---|---|---|
| `region-code-to-region-name:{code}` | Region name (String) | 24h | 区域编码→名称映射 |
| `train-station-relation-mapping:{start}_{end}` | Hash: trainId → TrainStationRelation JSON | 10min | 起终点→车次列表 |
| `train-info:{trainId}` | Train JSON / `{}` 空值 | 30min | 列车信息缓存 |
| `ticket-stocking-mapping:{trainId}_{start}_{end}` | JSONArray of Seat | 1min | 余票库存（高频） |
| `train-station:{trainId}_{startStation}` | TrainStation JSON | 1h | 列车区间时刻信息 |

#### 缓存策略

- **读穿透**: Cache-Aside 模式，先查 Redis，未命中查 DB，回写缓存
- **防击穿**: 查 DB 前加分布式锁（`SETNX`，TTL 10s），锁内 double-check
- **防穿透**: 查询不存在的 ID 缓存空值占位符 `{}`，TTL 30s
- **防雪崩**: 所有 set 写入加随机 jitter（±10% TTL）
- **缓存失效**: 选座锁定库存 / 取消订单释放座位后，通过 `takeoutStation()` 计算受影响区间，同步 delete 相应的 `ticket-stocking-mapping` key
- **价格查询**: 座位价格不再从 `t_seat` 获取，改为从 `t_train_station_price` 表按 (trainId, startStation, endStation, seatType) 查询

#### 分布式锁

锁 Key 前缀: `lock:`，通过 `SET key value NX EX 10` 实现，finally 块释放。

---

### 3.4 数据库设计

#### 核心 ER 关系

```
t_user 1──────N t_order 1──────N t_order_item
                     │                │
                     │                ├── seat_type, carriage_number, seat_number
                     │                └── real_name, id_card, amount
                     │
                     ├── t_ticket (座位锁定记录，含 purchaseMask)
                     │
                     ├── t_pay (支付记录, 1:1)
                     │
                     ├── t_refund_order (退票记录, 1:N, 一笔订单可多次部分退票)
                     │
                     └── t_change_order (改签记录, 1:N)

t_train 1──────N t_train_station (区间时刻)
        │
        ├──────N t_train_station_relation (起终点→车次映射)
        │
        ├──────N t_seat (座位，含位图、价格、状态，每物理座位一行)
        │
        ├──────N t_carriage (车厢)
        │
        └──────N t_train_station_price (区间票价)

t_passenger (乘车人，独立于用户)
```

#### 核心表说明

| 表 | 说明 |
|---|---|
| `t_user` | 用户（注册/登录） |
| `t_train` | 列车（车次、类型、品牌、起终点） |
| `t_train_station` | 列车区间时刻（经停车站的到发时间） |
| `t_train_station_relation` | 列车-站点关系（起终点→可乘车次查询） |
| `t_seat` | 座位（trainId + 车厢 + 座位号 + 座位类型 + 位图 + 价格 + 状态，每物理座位一行） |
| `t_order` | 订单（订单号、用户、车次、起终点、状态、时间） |
| `t_order_item` | 订单明细（乘客信息、座位、金额） |
| `t_ticket` | 车票（锁定记录，关联座位和乘客，含 purchaseMask 用于精确释放） |
| `t_pay` | 支付记录（流水号、渠道、金额、状态） |
| `t_refund_order` | 退票记录（退款单号、实退金额、手续费、退票张数） |
| `t_change_order` | 改签记录（改签单号、新旧车次/站点/金额、价差、手续费） |
| `t_passenger` | 乘车人（实名信息、证件） |
| `t_station` | 车站（编码、名称、所属地区） |
| `t_region` | 地区/城市（名称、编码、拼音） |
| `t_waitlist` | 候补记录（候补单号、订单号、车次、区间、座位类型、状态、截止时间） |
| `t_waitlist_passenger` | 候补乘客（关联候补记录和乘客，含选座偏好） |

#### 关键字段设计

- **主键**: `ASSIGN_ID`（雪花算法），`SnowflakeUtil` 自实现
- **时间字段**: `createTime`, `updateTime` 由 `MyMetaObjectHandler` 自动填充
- **逻辑删除**: `delFlag` (0-未删, 1-已删)，`@TableLogic`
- **金额字段**: `decimal(10,2)`（t_refund_order, t_change_order）和 `int(11)`（t_pay, t_seat, t_order_item）

---

### 3.5 订单生命周期

```
                 下单 createOrder
                     │
                     ▼
              ┌─────────────┐
              │   UNPAID    │ ─── cancelOrder ──→ CANCELED  (CLOSED)
              └──────┬──────┘
                     │ payOrder  → 生成 t_pay (PENDING)
                     ▼
              ┌─────────────┐
              │   PAID      │ ─── cancelOrder ──→ CANCELED  (REFUNDED)
              └──┬───┬───┬──┘
                 │   │   │
                 │   │   └── refund (部分退) ──→ PAID (部分 Ticket→REFUNDED)
                 │   │                                   │
                 │   │                          refund (全退) → CANCELED
                 │   │
                 │   └── change (改签) ──→ PAID (旧 Ticket→CHANGED, 新 Ticket→PAID)
                 │                              │
                 │                         Order 更新为新列车信息
                 │
                 └── 超时15min ──→ cancelOrder → CANCELED

                 候补购票流程
                     │
                     ▼
              ┌──────────────┐
              │   WAITLIST   │ ─── cancelWaitlist ──→ CANCELED  (FROZEN→REFUNDED)
              └──────┬───────┘
                     │ processWaitlist (自动匹配座位)
                     ▼
              ┌─────────────┐
              │   UNPAID    │ ─── 超时15min ──→ CANCELED  → 触发下一位候补
              └──────┬──────┘
                     │ payOrder
                     ▼
              ┌─────────────┐
              │   PAID      │
              └─────────────┘
```

#### 状态流转

| 阶段 | Order 状态 | OrderItem/Ticket 状态 | Pay 状态 |
|------|-----------|----------------------|---------|
| 下单 | UNPAID | UNPAID | — |
| 支付回调成功 | PAID | PAID | SUCCESS |
| 支付回调失败 | UNPAID | UNPAID | FAIL |
| 取消未支付订单 | CANCELED | CLOSED | — |
| 取消已支付订单 | CANCELED | REFUNDED | REFUNDED |
| 部分退票 | PAID | 被退票→REFUNDED / 其他→PAID | REFUNDED or PARTIAL_REFUND |
| 全额退票 | CANCELED | REFUNDED | REFUNDED |
| 改签 | PAID | 旧票→CHANGED / 新票→PAID | 价差>0: PENDING |
| 候补提交 | WAITLIST | — | FROZEN |
| 候补兑现 | UNPAID | UNPAID | SUCCESS |
| 候补取消/过期 | CANCELED | — | REFUNDED |

#### 订单列表查询

`GET /order/list` 支持按用户分页查询订单，通过 MyBatis-Plus 分页插件（`PaginationInnerInterceptor`）生成 LIMIT 子句。

- **筛选条件**: status（状态）、startDate/endDate（日期范围）、trainNumber（车次模糊匹配）
- **排序**: orderTime DESC，确保最新订单在前
- **分页限制**: size 最大 50，防刷
- **性能**: `t_order` 表建 `(user_id, status, order_time)` 联合索引
- **微服务化预备**: Order 表已冗余 trainNumber/startStation/endStation，列表查询不跨服务
- **响应**: `PageResponse<OrderListRespDTO>` — 通用分页结构，含 summary（totalAmount 汇总、passengerCount 计数），不含完整 OrderItem 详情

---

### 3.6 下单流程（核心）

```
1. OrderController.createOrder(reqDTO)
     │
2.   ChainContext.handler(ORDER_CREATE, reqDTO)
     │  ├── OrderCreateParamNotNullChainHandler   (参数非空)
     │  ├── OrderCreateParamVerifyChainHandler    (日期/格式)
     │  └── OrderCreateStockChainHandler          (库存预校验，位图查询)
     │
3.   OrderServiceImpl.createOrder()
     │  ├── 生成订单号 (Snowflake)
     │  ├── buildOrder() — 查列车信息+区间时刻 (带缓存)
     │  │
     │  ├── SeatSelector.selectSeats()
     │  │  ├── 查乘车人信息
     │  │  ├── 按 seatType 分组
     │  │  ├── 预加载列车站点序列，计算购买区间位图掩码 (bitmapMask)
     │  │  ├── 对每种 seatType:
     │  │  │  ├── 从 t_train_station_price 查票价
     │  │  │  ├── 加分布式锁 (座位+区间+类型)
     │  │  │  ├── 位图查询可用座位: WHERE (seat_bitmap & mask) = 0
     │  │  │  ├── CAS 单行原子锁定: SET seat_bitmap = seat_bitmap | mask
     │  │  │  └── 释放锁
     │  │  └── 失效全部重叠区间的余票缓存 (takeoutStation)
     │  │
     │  ├── 构建 Order, OrderItem, Ticket 实体
     │  └── 批量 insert (order + orderItems + tickets)
     │
4.   返回 OrderCreateRespDTO { orderSn, orderItems[] }
```

#### 位图座位模型

铁路售票的核心难点在于**区间复用**——同一物理座位可分段售卖。项目采用**位图模型**（借鉴 12306 设计）。

**数据模型**：每个物理座位一行，`seat_bitmap`（BIGINT）的每个 bit 代表一个相邻站点段的占用状态（1=占用, 0=空闲）。例如 5 站路线，BIT 0-3 分别对应 A→B、B→C、C→D、D→E 四个相邻段。

相比旧 C(n,2) 区间展开模型（5 站 = 每座 10 行），位图模型数据量 O(n)，且消除了"两个不重叠购买在包含区间行上虚假冲突"的问题。

**锁定逻辑**:
- 下单时指定起终点 → `bitmapMask()` 转换为位图掩码
- 查询: `WHERE (seat_bitmap & mask) = 0`（位图无交集 = 可用）
- CAS 锁定: `UPDATE SET seat_bitmap = seat_bitmap | mask WHERE id=? AND (seat_bitmap & mask) = 0`
- 单条 SQL 原子完成，affected rows=0 则说明被抢占

**释放逻辑**（取消/退票/改签时）:
- 从 `t_ticket.purchaseMask` 读取购票时落盘的位图掩码（不重新计算，避免站点变更导致的座位泄漏/超卖）
- CAS 释放: `UPDATE SET seat_bitmap = seat_bitmap & ~mask WHERE id=? AND (seat_bitmap & mask) = mask`
- 旧数据兼容：purchaseMask 为 null 时 fallback 重新计算
- 通过 `takeoutStation()` 计算受影响区间，失效对应余票缓存

**三个核心方法**:
- `bitmapMask(stations, departure, arrival)` — 购买区间 → 位图掩码（内部调用 `throughStation` 拆分原子段）
- `takeoutStation()` — 计算全部重叠区间（保留用于缓存失效）
- `throughStation()` — 拆分购买区间为相邻站点段

---

### 3.7 候补购票流程

参考 12306 候补机制，余票为 0 时允许用户排队等待退票释放座位，兑现后通知用户支付。

#### 核心设计决策

| 决策点 | 方案 | 理由 |
|--------|------|------|
| 入口 | 独立接口 `/order/waitlist-create` | 用户主动选择，不改造抢票流程 |
| 预付款 | 提交时冻结 Pay(FROZEN)，兑现时扣款 | 模拟 12306 预授权，展示设计思路 |
| 触发机制 | MQ 事件驱动 + 定时兜底 | 与抢票/超时取消 MQ 模式一致，多实例竞争消费 |
| 取消优先 | cancelWaitlist 降级为 cancelOrder | 用户取消意图始终成功 |
| 一致性 | DB 先写，Redis best-effort | DB 为真相源，30s 内自愈 |

#### 提交流程

```
POST /order/waitlist-create
  → 责任链校验 (参数非空 + 无重复候补 + 余票为0)
  → 创建 Order (status=WAITLIST)
  → 创建 Pay (status=FROZEN, amount=票价×人数)
  → 创建 Waitlist + WaitlistPassenger
  → ZADD Redis 候补队列 (事务提交后)
  → 返回 { waitlistSn, message }
```

#### 匹配流程（核心）

```
processWaitlist(trainId, seatType, startStation, endStation)
  → 获取分布式锁 (同一维度串行化)
  → ZRANGE Redis 队列取候选 (按时间排序)
  → 遍历候补:
      → DB 校验 status=WAITING (非 WAITING 则 SKIP + ZREM)
      → 检查是否过期 → 过期则标记 EXPIRED + 退款
      → CAS: WAITING → MATCHED
      → 选座锁座 (复用 SeatSelector.selectAndLockSeats)
        → 成功: Order→UNPAID, Pay→SUCCESS, 写 OrderItem/Ticket, 发超时消息
        → 失败(余票不足): 回退 WAITING，停止遍历
```

#### 触发时机

| 触发源 | 触发方式 |
|--------|----------|
| 用户取消订单 | `cancelOrder` → `triggerMatch` → MQ 消息 → `WaitlistMatchConsumer` → `processMatch` |
| 支付超时取消 | `OrderTimeoutCancelConsumer` → `cancelOrder` → MQ → Consumer → `processMatch` |
| 候补兑现超时 | 同上，走 `cancelOrder` 释放座位并触发下一位 |
| 定时兜底匹配 | `WaitlistMatchTask` @Scheduled 30s，从 DB 扫描 WAITING 记录，直接调用 `processWaitlist` |
| 定时过期清理 | `WaitlistExpireTask` @Scheduled 60s，清理 expire_time < NOW() 的记录 |

MQ 队列: `waitlist.match.exchange` → `waitlist.match.queue`，含 DLX 死信队列兜底。`triggerMatch` 发消息，`WaitlistMatchConsumer` 消费并调用 `processMatch` 遍历座位类型。定时兜底任务不走 MQ，直接调用 `processWaitlist`。

#### 取消 vs 匹配冲突处理

两者都走 DB CAS（`UPDATE WHERE status=WAITING`），InnoDB 行锁串行化，只有一个能赢：

```
cancelWaitlist(waitlistSn):
  查 Waitlist → orderSn
  status == WAITING:
    CAS: WAITING → CANCELED
    成功 → ZREM + 退款 (快速路径，无锁座)
    失败 → match 抢先了，fall through
  status != WAITING (已 MATCHED/EXPIRED/CANCELED):
    cancelOrder(orderSn) → 释放座位 + 退款 + 触发下一位候补
```

#### DB-Redis 一致性

- **写入**: DB INSERT → ZADD Redis（Redis 失败不重试，兜底任务补偿）
- **匹配**: Redis ZPOPMIN → DB SELECT 校验 → DB CAS 最终裁决
- **删除**: DB CAS UPDATE → ZREM Redis（Redis 失败无害）
- 兜底任务 30s 从 DB 读取 WAITING 记录，保证最终一致

#### 候补队列 Redis 结构

```
Key:    waitlist:queue:{trainId}_{seatType}_{startStation}_{endStation}
Type:   Sorted Set
Score:  创建时间戳 (毫秒)
Member: waitlist_sn
```

#### 支付状态流转

```
提交候补  → Pay(FROZEN)
兑现成功  → Pay(FROZEN → SUCCESS)   CAS
取消/过期 → Pay(FROZEN → REFUNDED)  CAS
超时未付  → Pay(SUCCESS → REFUNDED) 复用 cancelOrder
```

---

### 3.8 退票流程

支持部分退票（按 ticket 粒度），1206 阶梯手续费规则。

#### 核心流程

```
POST /order/{orderSn}/refund
  → 责任链校验 (参数非空 + 订单状态=PAID + 用户归属)
  → RefundServiceImpl.refund():
      1. 查 Order + 指定退票的 Ticket 列表
      2. 校验: 全部 ticket 须 PAID 且属于此订单
      3. 查 OrderItem 按 (carriageNumber, seatNumber) 建索引
      4. 逐张计算手续费: RefundChangeFeeCalculator.calculateRefundFee(票价, 发车时间, now)
      5. 创建 RefundOrder 退票记录 (实退金额 = 票面 - 手续费)
      6. CAS per-ticket: PAID → REFUNDED
      7. 释放座位: seat_bitmap = seat_bitmap & ~purchaseMask (从 ticket 读取落盘快照)
      8. 更新 OrderItem → REFUNDED
      9. 检查是否全部退票 → 是则 CAS Order PAID → CANCELED + Pay → REFUNDED
      10. 失效全部重叠区间余票缓存
      11. 事务提交后触发候补匹配
```

#### 手续费规则

| 退票时间（距开车） | 手续费率 |
|-------------------|---------|
| > 48 小时 | 免费 |
| 24 ~ 48 小时 | 10% |
| < 24 小时 | 20% |
| 已开车 | 不可退票 |

#### 并发安全

- **CAS per-ticket**: `UPDATE t_ticket SET ticket_status=REFUNDED WHERE id=? AND ticket_status=PAID`
- **座位释放 CAS**: `UPDATE t_seat SET seat_bitmap = seat_bitmap & ~mask WHERE (seat_bitmap & mask) = mask`
- 与 cancelOrder 并发冲突时，双方通过各自的 CAS 串行化，InnoDB 行锁保证只有一个赢

#### 退款记录表

`t_refund_order` 记录每次退票的实退金额、手续费、退票张数，用于审计和手续费分析。

---

### 3.9 改签流程

支持跨车次和同车次改签（整单改签），手续费阶梯收费，价差自动处理。

#### 核心流程

```
POST /order/{orderSn}/change
  → 责任链校验 (参数非空 + 订单状态=PAID + 用户归属)
  → ChangeServiceImpl.change():
      1. 查 Order + 全部 Ticket (要求整单改签，非全量拒绝)
      2. 保存旧值 (oldTrainId, oldStartStation, oldEndStation, oldDepartureTime)
      3. 确定新车次座位类型
      4. 按 trainId 升序获取两趟列车的分布式锁 (防死锁)
      5. 构建乘客列表 + 调用 SeatSelector.selectAndLockSeats() 锁新座位
      6. 计算手续费: 基于旧车次出发时间 (RefundChangeFeeCalculator.calculateChangeFee)
      7. 计算价差: 新票价 - 旧票价
      8. CAS per-ticket: PAID → CHANGED
      9. 释放旧座位 (用 ticket 落盘的 purchaseMask)
      10. 标记旧 OrderItem → CHANGED
      11. 创建新 Ticket + 新 OrderItem (PAID, 新列车信息)
      12. 更新 Order 列车信息为新列车
      13. 创建 ChangeOrder 改签记录
      14. 价差>0 创建补差 Pay(PENDING), 价差<0 记录日志
      15. 失效双方列车缓存 + 触发双方候补匹配
```

#### 手续费规则

| 改签时间（距开车） | 手续费率 |
|-------------------|---------|
| > 48 小时 | 免费 |
| 24 ~ 48 小时 | 5% |
| < 24 小时 | 15% |
| 已开车 | 不可改签 |

#### 防死锁设计

改签涉及两趟列车（旧→新），同时获取两把分布式锁时按 trainId **升序**获取：

```
lockFirst  = Math.min(oldTrainId, newTrainId)
lockSecond = Math.max(oldTrainId, newTrainId)
lock(lockFirst) → lock(lockSecond)
```

同车次改签（改座）只需一把锁，无需锁序处理。

#### 价差处理

| 情况 | 处理 |
|------|------|
| 新票 > 旧票 (补差价) | 创建 Pay(PENDING)，须支付差价后完成 |
| 新票 = 旧票 (平价) | 直接完成，无资金变动 |
| 新票 < 旧票 (退差价) | 创建 RefundOrder 差额退款 |

#### 改签记录表

`t_change_order` 记录新旧车次/站点/金额对比、价差、手续费，用于审计和改签历史追溯。

---

### 3.10 支付模块

支付模块采用**策略模式**设计，将支付渠道差异封装到 `PaymentStrategy` 接口中，通过 `PaymentService` 门面统一对外。从 `OrderServiceImpl` 剥离独立的支付服务层，降低耦合度。

#### 架构层次

```
OrderController  ──→  OrderService.payOrder()    ← 责任链 ORDER_PAY 参数校验
                           │
                           ▼
                    PaymentService                 ← 门面，渠道路由
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        MockStrategy  AlipayStrategy  WechatStrategy   ← 策略实现
              │
              ▼
        MockPayController                            ← 模拟支付页面
```

#### 策略接口

```java
public interface PaymentStrategy {
    String getChannel();                          // 渠道标识: MOCK / ALIPAY / WECHAT
    PayCreateResult createPayment(PayCreateRequest req);
    boolean verifySignature(PayCallbackReqDTO callback);
    String queryStatus(String orderSn);
    boolean refund(String orderSn, Integer amount);
}
```

`PaymentStrategy` 由 Spring 自动收集（`List<PaymentStrategy>` 注入），按 `getChannel()` 路由。新增支付渠道只需添加一个策略实现类，无需修改门面代码。

#### 支付流程

```
POST /order/{orderSn}/pay
  → 责任链 ORDER_PAY 参数校验
  → OrderServiceImpl.payOrder() → PaymentService.createPayment()
  → 按 channel 路由到 PaymentStrategy.createPayment()
  → MockPaymentStrategy:
      1. 查重防重复支付 (uk_order_sn 兜底)
      2. 计算订单总金额
      3. 生成 paySn
      4. HMAC-SHA256(paySn|orderSn|totalAmount|status) 生成签名
      5. 写 t_pay (PENDING, channel=MOCK, tradeNo=签名)
      6. 返回 PayCreateResult { paySn, payUrl: "/mock-pay/{paySn}", sign }

POST /order/pay/notify  (回调)
  → 责任链 PAY_NOTIFY:
      order=0: PayNotifyParamNotNullChainHandler    (参数非空)
      order=3: PayNotifySignVerifyChainHandler      (签名校验)
  → PaymentService.handleCallback()
  → CAS: UPDATE t_order SET status=PAID WHERE status=UNPAID
  → 更新 OrderItem/Ticket/Pay 状态
  → CAS 失败时: 已 PAID→幂等, 已 CANCELED→记录待退款

POST /mock-pay/{paySn}/pay  (模拟支付确认)
  → MockPayController 构造 PayCallbackReqDTO (含签名)
  → PaymentService.handleCallback()
  → 走完整回调链路，验证签名校验 + CAS 状态机
```

#### HMAC-SHA256 签名机制

Mock 策略使用 HMAC-SHA256 对称签名，密钥通过 `payment.mock.secret` 配置。签名规则：

```
sign = Base64(HMAC-SHA256(paySn + "|" + orderSn + "|" + totalAmount + "|" + status, secretKey))
```

`createPayment` 时将签名存入 `t_pay.trade_no`，`verifySignature` 时从 Pay 表读取 `paySn` 后重新计算比对。签名校验在责任链中统一执行（`PayNotifySignVerifyChainHandler`），不通过直接拒绝，不进入业务逻辑。

#### Pay 状态枚举

```java
public enum PayStatusEnum {
    PENDING("PENDING"),           // 待支付
    SUCCESS("SUCCESS"),           // 支付成功
    FAIL("FAIL"),                 // 支付失败
    FROZEN("FROZEN"),             // 预授权冻结（候补）
    PENDING_REFUND("PENDING_REFUND"), // 待退款
    REFUNDED("REFUNDED");         // 已退款
}
```

所有 Pay 状态引用统一使用枚举，替换原先散落各处的硬编码字符串。枚举值保持与数据库存储格式一致，不影响已有数据。

#### 与 OrderService 的关系

`OrderServiceImpl` 不再直接操作 Pay 表。支付创建 (`payOrder`)、回调处理 (`handlePayNotify`)、Pay 记录读写 (`saveOrUpdatePay`) 全部移至 `PaymentServiceImpl`。`OrderServiceImpl` 仅保留 `ORDER_PAY` 责任链调用，随后委托 `PaymentService.createPayment()`。

`cancelOrder` 中更新 Pay → REFUNDED 的操作使用 `PayStatusEnum.REFUNDED.getCode()`，不再使用字符串硬编码。

---

## 4. API 接口

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/auth/login` | 手机号+密码登录 | 否 |
| POST | `/auth/register` | 用户注册 | 否 |
| GET | `/ticket/query` | 余票查询（分页） | 否 |
| POST | `/order/create` | 创建订单 | 是 |
| POST | `/order/{orderSn}/pay` | 模拟支付（返回 `PayCreateResult` 含支付链接） | 是 |
| GET | `/order/{orderSn}` | 订单详情（含 ticketId） | 是 |
| POST | `/order/{orderSn}/cancel` | 取消订单 | 是 |
| POST | `/order/{orderSn}/refund` | 退票（支持部分退票） | 是 |
| POST | `/order/{orderSn}/change` | 改签（跨车次/同车次） | 是 |
| POST | `/order/pay/notify` | 支付结果回调（验签 + CAS 状态更新） | 否 |
| GET | `/mock-pay/{paySn}` | 模拟支付页面（订单金额、支付确认） | 否 |
| POST | `/mock-pay/{paySn}/pay` | 模拟支付确认（走完整回调链路） | 否 |
| POST | `/order/waitlist-create` | 提交候补订单 | 是 |
| GET | `/order/waitlist/{waitlistSn}` | 查询候补状态 | 是 |
| POST | `/order/waitlist/{waitlistSn}/cancel` | 取消候补 | 是 |
| GET | `/user/me` | 查看个人信息（脱敏） | 是 |
| PUT | `/user/update` | 修改个人资料（idCard 加密存储） | 是 |
| POST | `/user/change-password` | 修改密码 | 是 |
| POST | `/user/delete` | 注销账号（软删除） | 是 |
| GET | `/order/list` | 订单列表分页查询（支持状态/日期/车次筛选） | 是 |

---

## 5. 统一响应格式

```json
{
  "code": "0",
  "message": "success",
  "data": { ... },
  "requestId": null
}
```

- `code: "0"` 表示成功
- 异常由 `GlobalExceptionHandler` 统一拦截，返回 `Result.fail(errorMessage)`

---

## 6. 异常处理

```
ClientException  ── HTTP 400 — 客户端错误（参数非法、未登录、token 过期）
ServiceException ── HTTP 500 — 服务端错误（系统繁忙、库存不足）
```

`GlobalExceptionHandler` (`@RestControllerAdvice`) 拦截所有 `Exception`，统一返回 `Result.fail(e.getMessage())`。

---

## 7. 编码规范

| 规范 | 说明 |
|------|------|
| 构造注入 | `@RequiredArgsConstructor` + `private final` |
| 实体基类 | `BaseEntity` — 雪花 ID, createTime, updateTime, delFlag |
| DTO 分层 | `dto/req/` 请求, `dto/resp/` 响应, `dto/` 内部传输 |
| 表前缀 | `t_` (t_train, t_order, t_seat ...) |
| 金额单位 | 分 (int) |
| 参数校验 | 统一放责任链，Service 层不写内联 if-null |
| 缓存 TTL | 常量定义在 `RedisConstant`，无硬编码 |
| 锁释放 | 统一在 finally 块 delete |
| 分页响应 | `PageResponse<T>` (common/page/) 统一封装，提供 `from(IPage, records)` 工厂方法 |
| 敏感数据 | 身份证号 AES 加密存储（`AesUtil`），响应中脱敏展示；密码 BCrypt 不可逆加密 |
