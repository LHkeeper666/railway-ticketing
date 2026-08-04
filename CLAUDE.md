# railway-ticketing

铁路售票系统后端项目。提供余票查询、订单创建与支付、抢票排队、订单超时取消、用户认证等核心功能。

## 技术栈

- **框架**: Spring Boot 3.5.13 + JDK 17
- **ORM**: MyBatis-Plus 3.5.10 (spring-boot3-starter)
- **数据库**: MySQL 5.7 (本地 Docker 开发)
- **缓存**: Redis (Spring Data Redis + Lettuce)
- **消息队列**: RabbitMQ (spring-boot-starter-amqp)
- **JSON**: FastJSON2 2.0.36
- **JWT**: jjwt 0.12.6 (HS256)
- **密码**: DelegatingPasswordEncoder (Spring Security Crypto, {bcrypt}/{noop})
- **工具**: Lombok, MyBatis-Plus Generator + Velocity
- **构建**: Maven (含 mvnw 包装器)

## 项目结构

```
src/main/java/com/lhkeeper/ticketing/railway_ticketing/
├── RailwayTicketingApplication.java    # 启动入口 @MapperScan
├── common/
│   ├── annotation/RateLimit.java       # 令牌桶限流注解 (capacity, refillRate)
│   ├── constant/RedisConstant.java     # Redis key 模板、缓存 TTL、分布式锁、限流常量
│   ├── mybatis/MyMetaObjectHandler.java # 自动填充(createTime/updateTime/delFlag)
│   ├── page/PageRequest.java           # 分页请求基类 (current, size)
│   └── result/Result.java              # 统一响应体 (code, message, data)
├── config/
│   ├── RabbitMQConfig.java             # 抢票队列(DLX) + 订单超时延迟队列(TTL+DLX)
│   ├── RedisConfig.java                # Redis 连接与序列化配置
│   ├── SecurityBeanConfig.java         # DelegatingPasswordEncoder Bean
│   └── WebMvcConfig.java               # 注册 RateLimitInterceptor(order 0) + JwtInterceptor(order 1)
├── context/
│   ├── UserInfo.java                   # 用户信息 DTO (userId, username, phone)
│   └── UserContext.java                # ThreadLocal 持有当前请求用户
├── controller/
│   ├── AuthController.java             # /auth/login, /auth/register
│   ├── TicketController.java           # /ticket/query 余票查询 (@RateLimit), /ticket/health
│   └── OrderController.java            # /order/create, /order/flash-create, /order/{sn}/pay,
│                                       #   /order/pay/notify, /order/{sn} 详情, /order/{sn}/cancel
├── domain/
│   ├── dto/req/                        # 请求 DTO
│   ├── dto/resp/                       # 响应 DTO
│   ├── dto/                            # 内部传输 DTO (含 @Builder)
│   ├── entity/                         # MyBatis-Plus 实体 (extends BaseEntity)
│   └── enums/                          # ChainMark, SeatStatus, SeatType, OrderStatus, TicketStatus
├── exception/
│   ├── ServiceException.java           # 服务端异常 (http 500)
│   ├── ClientException.java            # 客户端异常 (http 400)
│   └── GlobalExceptionHandler.java     # @RestControllerAdvice 全局处理
├── interceptor/
│   ├── JwtInterceptor.java             # 从 Authorization Bearer 提取 JWT，设置 UserContext
│   └── RateLimitInterceptor.java       # 令牌桶限流 (Redis Lua 脚本)
├── mapper/                             # Mapper 接口 (extends BaseMapper)
├── service/
│   ├── AuthService.java / impl/AuthServiceImpl.java
│   ├── TicketService.java / impl/TicketServiceImpl.java
│   ├── OrderService.java  / impl/OrderServiceImpl.java
│   ├── OrderItemService.java / impl/OrderItemServiceImpl.java
│   └── handler/
│       ├── filter/                     # 责任链模式 — 参数校验/业务校验
│       │   ├── AbstractChainFilter.java      # 顶层接口 (继承 Ordered)
│       │   ├── AbstractChainContext.java     # 注册&执行容器 (CommandLineRunner)
│       │   ├── *ChainFilter.java            # 按业务分组的标记接口
│       │   └── *ChainHandler.java           # @Component 具体实现
│       ├── mq/
│       │   ├── FlashOrderConsumer.java       # 抢票队列消费者 (selectAndLockSeats → UNPAID)
│       │   └── OrderTimeoutCancelConsumer.java # 超时取消消费者 (检查状态 → cancelOrder)
│       ├── select/SeatSelector.java   # 选座逻辑 (selectSeats / selectAndLockSeats)
│       └── task/
│           └── PendingOrderCleanupTask.java  # 兜底扫描超时 PENDING 订单取消
└── util/
    ├── SnowflakeUtil.java              # 雪花算法 ID 生成器
    ├── DateUtil.java                   # 日期校验
    ├── StringUtil.java                 # 字符串空判断
    ├── JwtUtil.java                    # JWT 生成与解析 (HS256)
    └── StationCalculateUtil.java       # 列车区间计算 (重叠区间/拆分区间)
```

## 架构模式

### 分层架构
Controller → Service(接口+实现) → Mapper(接口+XML)

### 责任链校验 (Chain of Responsibility)
- `AbstractChainFilter<T>` — 顶层接口，继承 `Ordered`，含 `handler(T)` 和 `mark()`
- `AbstractChainContext` — 启动时自动扫描所有 `AbstractChainFilter` Bean，按 `mark()` 分组、按 `getOrder()` 排序
- 业务分组标记接口: `OrderCreateChainFilter`, `TicketQueryChainFilter`, `OrderPayChainFilter`, `AuthLoginChainFilter`, `AuthRegisterChainFilter`, `PayNotifyChainFilter`, `OrderCancelChainFilter`
- 使用: `abstractChainContext.handler(ChainMarkEnum.XXX.name(), requestParam)`
- **规范: 前端请求的参数校验，都放到抽象责任链中。** Service 层不写 `if (xx == null) throw...`
- 注意: 需要手动给具体 Handler 类加 `@Component`，否则不会被扫描

### 认证流程
```
POST /auth/login    → AuthController → AuthServiceImpl
                       ├── loginChainContext.handler(AUTH_LOGIN, reqDTO)  # 参数非空校验
                       ├── 查 User 表，比对密码 (DelegatingPasswordEncoder)
                       └── JwtUtil.generateToken(userId, username, phone) → LoginRespDTO.token

后续请求 Authorization: Bearer <token>
  → JwtInterceptor.preHandle()
    → 解析 JWT → UserContext.set(new UserInfo(...))
    → Service 层通过 UserContext.get() 获取当前用户
    → afterCompletion → UserContext.clear()
```

JwtInterceptor 排除路径: `/auth/login`, `/auth/register`, `/ticket/**`, `/order/pay/notify`

### 缓存策略
- Redis 缓存区域名映射、车次关系、列车信息、余票库存
- 查缓存 → 未命中查 DB → 分布式锁(防缓存击穿) → 写缓存
- **TTL 策略** (均为常量，无硬编码):
  - 区域映射 1d, 车次信息 30min, 车次关系 10min, 余票库存 1min, 列车区间 1h, 空值缓存 30s
  - 所有 set 写入均加随机 jitter (±10%) 防缓存雪崩
- **空值缓存**: 查询不存在的 ID 缓存占位符 `{}`，TTL 30s，防缓存穿透
- **Region 缓存**: 首次加载标记 `cache:region:loaded` 存入 Redis (替代静态 volatile 标志)，解决 DCL 问题
- **缓存失效**: 选座锁定库存后/取消订单释放座位后同步调用 `stringRedisTemplate.delete(stockCacheKey)`
- 锁常量: `LOCK_KEY_PREFIX = "lock:"`, `LOCK_TTL_SECONDS = 10L`

### 令牌桶限流
- `@RateLimit(key, capacity, refillRate)` 注解在 Controller 方法上
- `RateLimitInterceptor` 通过 Redis Lua 脚本实现令牌桶算法
- key 前缀: `rate_limit:`, 过期时间: 3600s
- 限流拒绝抛出 `ClientException("请求过于频繁，请稍后重试")`

### 抢票 (Flash Order) 流程
```
POST /order/flash-create
  → 责任链校验参数
  → 生成 orderSn，写 Order (status=PENDING)
  → 发送 MQ 消息 (flash.order.exchange → flash.order.queue)
  → 立即返回 FlashOrderCreateRespDTO (排队提示)

FlashOrderConsumer 消费:
  → 幂等检查 (order.status == PENDING)
  → SeatSelector.selectAndLockSeats() 选座锁定
  → 写 OrderItem / Ticket，更新 Order → UNPAID
  → sendOrderTimeoutMessage() 发送延迟取消消息
  → 失败则更新 Order → CANCELED
```

### 订单超时取消
- 订单创建时通过 RabbitMQ TTL + DLX 实现延迟消息
- 延迟队列 `order.timeout.delay.queue` (x-dead-letter-exchange → `order.timeout.dlx`)
- 超时时间: 15 分钟 (`ORDER_TIMEOUT_MS`)
- `OrderTimeoutCancelConsumer` 消费 `order.timeout.cancel.queue`:
  - 检查 order.status == UNPAID 才执行 cancelOrder
  - 已支付/已取消的订单直接 ack 跳过

### 订单取消 (cancelOrder)
- 责任链校验 → 查订单，幂等检查
- 通过 `StationCalculateUtil.takeoutStation()` 计算全部重叠区间
- 释放座位 (LOCKED → AVAILABLE)
- 更新订单状态 CANCELED
- 更新 OrderItem / Ticket: 已支付→REFUNDED, 未支付→CLOSED
- 已支付则更新 Pay 表为 REFUNDED
- 删除全部重叠区间的余票缓存

### 支付回调 (Pay Notify)
- `POST /order/pay/notify` (无需 JWT，排除在拦截器外)
- 责任链校验 → 查订单，幂等检查
- 支付成功: 更新 Order(PAID), OrderItem(PAID), Ticket(PAID), Pay(SUCCESS)
- 支付失败: 仅更新 Pay(FAIL)

## 编码规范

### 包结构
- 基础包: `com.lhkeeper.ticketing.railway_ticketing`
- mapper 扫描: `@MapperScan("com.lhkeeper.ticketing.railway_ticketing.mapper")`

### Controller
- `@RestController` + `@RequestMapping("/模块名")`
- 方法返回 `Result<T>` 统一包装
- 需要限流的方法加 `@RateLimit` 注解
- 示例:
```java
@RateLimit(key = "ticket:query", capacity = 100, refillRate = 50.0)
@GetMapping("/query")
public Result<TicketPageQueryRespDTO> query(TicketPageQueryReqDTO req) {
    return Result.success(service.query(req));
}
```

### Service
- 接口继承 `IService<Entity>`，实现继承 `ServiceImpl<Mapper, Entity>`
- 构造注入: `@RequiredArgsConstructor` + `private final` 字段
- 事务: `@Transactional(rollbackFor = Throwable.class)`
- 不再写内联参数校验 — 统一交给责任链

### Entity
- extends `BaseEntity` (id雪花算法, createTime, updateTime, delFlag逻辑删除)
- 注解: `@Data @NoArgsConstructor @AllArgsConstructor @TableName("t_xxx")`
- `@TableField` 显式指定列名，主键用 `@TableId(type = IdType.ASSIGN_ID)`
- 数据库表前缀: `t_` (t_train, t_order, t_seat 等)

### DTO
- 请求 DTO 放 `dto/req/`，响应 DTO 放 `dto/resp/`，内部传输放 `dto/`
- `@Data @Builder @NoArgsConstructor @AllArgsConstructor`

### 枚举
- `@RequiredArgsConstructor` + `@Getter` 模式
- 示例: `SeatStatusEnum`、`TicketStatusEnum`、`SeatTypeEnum`
- 注意: `OrderStatusEnum` 未使用 `@Getter`，用的是自定义 `getCode()`

### 异常
- 客户端异常(参数校验失败、限流拒绝): `throw new ClientException("消息")`
- 服务端异常(业务逻辑失败): `throw new ServiceException("消息")`
- `GlobalExceptionHandler` 统一拦截 Exception 返回 `Result.fail(msg)`

### 分页
- `PageRequest` (current, size) 可作为请求 DTO 的基类

### 用户上下文
- `UserContext.get()` 获取当前请求用户 (ThreadLocal，通过 JwtInterceptor 注入)
- `UserContext.get().getUserId()` / `getUsername()` 用于订单创建等业务

### Result 统一响应
```json
{
  "code": "0",       // "0" 表示成功
  "message": "...",
  "data": {...},
  "requestId": null
}
```

## 启动方式

### 依赖服务 (Docker)
```bash
# 一键初始化 MySQL + Redis
bash docker-env/main.sh

# 或手动
cd docker-env
docker compose up -d
```

### 应用
```bash
./mvnw spring-boot:run
# 或直接运行 RailwayTicketingApplication.java
```

### 接口
- `POST /auth/login` — 手机号+密码登录 (无需 token)
- `POST /auth/register` — 用户注册 (无需 token)
- `GET  /ticket/query` — 余票查询
- `GET  /ticket/health` — 健康检查
- `POST /order/create` — 创建订单 (需 Authorization header, @RateLimit)
- `POST /order/flash-create` — 抢票排队 (需 Authorization header, @RateLimit)
- `POST /order/{orderSn}/pay` — 模拟支付 (需 Authorization header)
- `POST /order/pay/notify` — 支付回调 (无需 token)
- `GET  /order/{orderSn}` — 订单详情
- `POST /order/{orderSn}/cancel` — 取消订单

## 已知问题 / TODO

### 本次面试模拟发现的 Bug（2026-05-14）

- [ ] **Region 缓存无 TTL**：`TicketQueryParamVerifyChainHandler` 第 47 行 `set(REGION_LOADED_FLAG, "1")` 未设过期时间，且单个 Region 数据 key 也未设 TTL（常量 `CACHE_TTL_REGION = 86400` 已定义但未使用）。DB 新增区域后缓存永不过期。同时 Region 缓存锁竞争失败直接抛 `ServiceException("系统正忙")`，Redis 重启后 99% 请求返回 500，应改为自旋重试。
- [x] **PENDING 兜底任务已修复**：`cancelOrder` 已增加 PENDING 快速路径（无 Ticket/OrderItem，CAS 直接改状态 CANCELED）。详见 `OrderServiceImpl.cancelOrder` 行 667。
- [x] **handlePayNotify 已修复**：`handlePayNotify` 和 `cancelOrder` 均已重构为 CAS 模式（`UPDATE WHERE status=?`），不再有"快照读 vs FOR UPDATE"的不对称问题。两个 CAS 在 MySQL InnoDB 行锁层面串行化，只有一个能赢。失败者递归重试读取最新状态，不存在 TOCTOU 窗口。详见 `OrderServiceImpl.handlePayNotify`、`cancelOrder`。
- [x] **cancelOrder 重新计算 purchaseMask 已修复**：选座时将 `purchaseMask` 存入 `t_ticket` 表，取消/退票/改签时从 ticket 读取落盘快照，不重新计算。旧数据兼容：purchaseMask 为 null 时 fallback 重新计算。Schema 变更已并入 `db_table.sql`。
- [x] **OrderTimeoutCancelConsumer 预检查已隐含修复**：`cancelOrder` 改为 CAS 模式后，重复消息的第二条 CAS 失败 → 递归重读 → 看到 CANCELED → 直接返回，不再执行完整取消流程，浪费 DB 资源的问题已消除。
- [ ] 部分注释代码未清理

### 抢票异步消费的数据一致性问题（全部已修复）

- [x] **DB 与 MQ 双写不一致**：`flashCreateOrder` 中 DB INSERT 和 `convertAndSend` 不在同一事务，Consumer 可能在 Producer 事务提交前收到消息，`selectOne` 查不到订单 → 静默 ACK → 消息丢失，订单永久 PENDING。
  - **已修复**：`TransactionSynchronizationManager.registerSynchronization().afterCommit()` 事务提交后发 MQ + consumer 侧 `order==null` 时抛 `ServiceException` 触发 `nack(requeue=true)` 重试。详见 `OrderServiceImpl.flashCreateOrder`、`processFlashOrder`、`FlashOrderConsumer.onMessage`。
- [x] **幂等检查 TOCTOU 竞态**：`processFlashOrder` 的状态检查 `selectOne` 是普通 SELECT 无行锁，两条相同消息并发时可能同时通过 PENDING 检查，分别锁到不同的座位，产生双份 OrderItem/Ticket。
  - **已修复**：在 `ORDER BY order_sn` 查询上追加 `.last("FOR UPDATE")` 对订单行加悲观锁，串行化对同一订单的并发处理。详见 `OrderServiceImpl.processFlashOrder`。
- [x] **消息失败无重试**：`FlashOrderConsumer.onMessage` catch 中 `basicNack(requeue=false)` + `setDefaultRequeueRejected(false)`，所有异常（含临时故障）直接进 DLQ，DLQ 无消费者，消息永久丢失。
  - **已修复**：三级异常分流——`SeatSelector` 对永久业务错误（余票不足、无乘车人）改用 `ClientException`；`processFlashOrder` 只 catch `ClientException` 设 CANCELED；临时故障（`ServiceException`、`DataAccessException`）向上传播，Consumer 统一 `nack(requeue=true)`。详见 `SeatSelector`、`OrderServiceImpl.processFlashOrder`、`FlashOrderConsumer.onMessage`。
- [x] **PENDING 订单无兜底清理**：消息一旦丢失，订单永久 PENDING。
  - **已修复**：新增 `PendingOrderCleanupTask`（`service/task/`），`@Scheduled(fixedDelay=60s)` 每分钟扫描 `status=PENDING AND create_time < now-15min` 的订单，复用 `cancelOrder` 兜底取消，加 Redis 分布式锁防止多实例并发。详见 `PendingOrderCleanupTask`。

## 数据库

表均以 `t_` 为前缀，主要表包括:
- `t_train` — 列车
- `t_seat` — 座位 (含价格、状态)
- `t_order` / `t_order_item` — 订单与订单项
- `t_ticket` — 车票
- `t_pay` — 支付记录
- `t_passenger` — 乘车人
- `t_station` / `t_train_station` — 站点
- `t_train_station_relation` — 列车-站点关系
- `t_carriage` — 车厢
- `t_region` — 区域/城市
- `t_user` — 用户

### 退票/改签功能（2026-08-04 新增）

#### 退票 `POST /order/{orderSn}/refund`
- 支持部分退票（按 ticket 粒度），退票后未被退的 ticket 仍为 PAID
- 手续费参考 12306 阶梯：>48h 免费，24-48h 10%，<24h 20%，开车后不可退
- CAS per-ticket：逐张 CAS `PAID → REFUNDED`，释放座位，更新 OrderItem
- 全部退票时 CAS Order `PAID → CANCELED` + Pay → REFUNDED
- 释放座位后触发候补匹配
- 新增表 `t_refund_order`，新建 `RefundService` / `RefundServiceImpl`
- 责任链: `OrderRefundChainFilter` → `OrderRefundParamNotNullChainHandler`(order 0) + `OrderRefundStatusChainHandler`(order 1)

#### 改签 `POST /order/{orderSn}/change`
- 支持跨车次/同车次改签（整单改签，须选择全部 PAID ticket）
- 手续费参考 12306 阶梯：>48h 免费，24-48h 5%，<24h 15%，开车后不可改
- 分布式锁按 trainId 升序获取，防死锁（同车次改签只需一把锁）
- 流程: 锁新车次座位 → 选座并锁定 → CAS 旧 ticket `PAID → CHANGED` → 释放旧座位(用 purchaseMask) → 创建新 Ticket/OrderItem(`PAID`) → 更新 Order 列车信息 → 创建 ChangeOrder 记录
- 价差>0 创建补差 Pay(PENDING)，价差<0 记录日志待退款
- 双方列车缓存均失效，双方均触发候补匹配
- 新增表 `t_change_order`，新建 `ChangeService` / `ChangeServiceImpl`
- 责任链: `OrderChangeChainFilter` → `OrderChangeParamNotNullChainHandler`(order 0) + `OrderChangeStatusChainHandler`(order 1)

