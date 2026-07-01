## Why

当前系统在余票不足时，抢票失败直接取消订单（status→CANCELED），用户无法在有退票时自动获得座位。参考 12306 候补购票机制，需要增加候补功能：余票为 0 时允许用户排队等待，有退票释放座位时自动兑现，兑现后通知用户支付。

## What Changes

- 新增候补下单接口 `POST /order/waitlist-create`，与抢票流程并存
- 新增候补状态查询和取消接口
- 新增 `t_waitlist` 和 `t_waitlist_passenger` 表，存储候补队列和乘客信息
- 订单状态枚举新增 `WAITLIST(4)`
- 支付状态新增 `FROZEN`，候补提交时冻结预付款，兑现时扣款，取消/过期时解冻
- 新增事件驱动触发：`cancelOrder` 释放座位后立即检查候补队列
- 新增定时兜底任务：每 30s 从 DB 扫描未匹配的候补记录，每 60s 清理过期候补
- 新增 `WaitlistCreateChainHandler` 责任链校验
- `cancelOrder` 新增 `WAITLIST` 快速路径，支持候补订单取消

## Capabilities

### New Capabilities
- `waitlist-order`: 候补购票核心流程 — 提交候补、候补匹配、取消候补、过期处理、预付款冻结/解冻/扣款
- `waitlist-queue`: 候补队列管理 — Redis Sorted Set 排队、DB-Redis 一致性保证、事件驱动触发、定时兜底扫描

### Modified Capabilities
- `order-cancel`: 订单取消流程新增候补触发 — cancelOrder 释放座位后检查候补队列，为下一位候补用户分配座位；新增 WAITLIST 状态快速路径

## Impact

- **新增文件**: 2 个实体、1 个枚举、2 个 Mapper、1 个 Service 接口+实现、1 个责任链 Handler、2 个定时任务、3 个 DTO
- **修改文件**: `OrderStatusEnum`(新增枚举值)、`OrderServiceImpl.cancelOrder`(新增 WAITLIST 路径+候补触发)、`OrderController`(新增 3 个接口)、`RedisConstant`(新增 key 常量)
- **数据库**: 新增 2 张表 (`t_waitlist`, `t_waitlist_passenger`)
- **Redis**: 新增 Sorted Set 类型的候补队列 key
- **依赖**: 无新增外部依赖，使用现有 MyBatis-Plus、Redis、Spring Scheduled
