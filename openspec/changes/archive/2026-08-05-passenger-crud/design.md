## Context

项目现有 `t_passenger` 表、`Passenger` Entity、`PassengerMapper` 均已在数据库和代码层定义完成，下单/退票/候补流程中已通过 `PassengerMapper` 做只读查询。缺失的是面向前端的 CRUD 入口（Controller + Service + 校验链）。项目已有成熟的分层架构和责任链校验模式，本次实现完全复用现有模式。

## Goals / Non-Goals

**Goals:**
- 提供乘车人创建/修改/删除/列表查询的完整 REST API
- 遵循项目已有的责任链校验模式（参数非空 → 业务校验 → Service 执行）
- 添加 `user_id` 字段，以不可变主键关联用户，为微服务化做准备
- 响应数据对证件号和手机号做展示脱敏
- 删除乘客时校验无进行中订单

**Non-Goals:**
- 不引入缓存层（乘车人数据量 ≤15/用户，DB 查询足够）
- 不做详情接口（列表已返回全量字段）
- 不做分页（上限 15 条）
- 不修改证件号码（需删了重建）
- 不改变现有下单/退票流程中对 `PassengerMapper` 的只读调用

## Decisions

### 1. 用户关联从 username 迁移到 userId

**选择**: 新增 `Passenger.userId` 字段（bigint），保留 `username` 字段兼容旧代码。

**理由**: `username` 可变（用户改名），`userId`（雪花 ID）不可变。微服务化后跨服务通信使用 `userId` 作为乘客归属标识，不依赖可能变化的用户名字符串。

**替代方案**: 完全替换 `username` 为 `userId`。放弃原因：需改动所有现有代码中的 `Passenger::getUsername` 引用，风险大且无必要。

### 2. 责任链按操作分离

**选择**: `PASSENGER_CREATE` / `PASSENGER_UPDATE` / `PASSENGER_DELETE` 三条独立链。

**理由**: 每种操作的校验逻辑不同（创建校验必填字段+数量上限，修改校验归属+字段合法性，删除校验归属+订单依赖）。与项目现有模式一致（`ORDER_CREATE` / `ORDER_PAY` / `ORDER_CANCEL` 均为独立链）。

### 3. PassengerService 不继承 IService<Passenger>

**选择**: `PassengerService` 定义为纯业务接口，不继承 MyBatis-Plus `IService<Passenger>`。

**理由**: 接口即契约，微服务化后可直接作为 Feign 接口使用。MyBatis-Plus 的 `IService` 暴露了大量通用 CRUD 方法（`save`, `updateById`, `lambdaQuery` 等），不适合对外暴露。ServiceImpl 内部直接使用 `PassengerMapper`。

**替代方案**: 继承 `IService<Passenger>`。放弃原因：Feign 接口不需要 `IService` 的能力，且会污染外部契约。

### 4. 证件信息不可修改

**选择**: `PUT /passenger/{id}` 只接受 `phone` 和 `discountType` 两个字段。

**理由**: 参考 12306 规则，证件号码代表乘车人身份，修改证件号码等于换人，应删了重建。

### 5. 身份证/手机号展示脱敏

**选择**: 在 DTO 构建时脱敏（不是在 Entity 层或 DB 层）。

**理由**: 
- DB 存储明文（开发阶段可接受，后续微服务化时统一加密）
- DTO 层脱敏保证所有对外输出一致
- 内部流程（下单时获取乘客信息写 Ticket 快照）通过 Service 内部方法获取完整值，不受脱敏影响

## Risks / Trade-offs

- **[数据完整性] user_id 字段新增后，存量数据需回填** → 发布时执行 `UPDATE t_passenger p JOIN t_user u ON p.username = u.username SET p.user_id = u.id WHERE p.user_id IS NULL`，代码中写入时同时设 `username` 和 `userId`
- **[兼容性] 现有代码中 `passengerMapper.selectByIds()` 只读调用不受影响** → 下单/退票/候补流程无需改动
- **[DDL 变更] 加列操作在生产 MySQL 5.7 上可能锁表** → `ALTER TABLE ... ADD COLUMN` 在 MySQL 5.7 InnoDB 上通常是 online DDL（允许并发 DML），数据量小(<1万行)影响可忽略
- **[扩展性] 后续微服务化时 `PassengerMapper` 直接注入需改为 RPC 调用** → 这些注入点（`OrderCreateParamVerifyChainHandler`, `SeatSelector`, `WaitlistServiceImpl`）后续替换为 `PassengerService` 内部调用即可，接口不变
