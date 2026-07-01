## Context

当前铁路售票系统的抢票流程（`flashCreateOrder`）在余票不足时直接将订单标记为 CANCELED，没有排队等待机制。用户需要反复重试，体验差。

现有系统关键约束：
- 座位模型基于位图（`seat_bitmap`），区间锁定通过位运算实现
- 订单状态：UNPAID(0), PAID(1), CANCELED(2), PENDING(3)
- 抢票异步消费通过 RabbitMQ，失败后 nack(requeue=true) 重试
- `cancelOrder` 使用 CAS 模式（UPDATE WHERE status=?），与支付回调互斥
- 分布式锁用于座位选择（`seat:train:{id}:type:{type}`）

## Goals / Non-Goals

**Goals:**
- 余票为 0 时提供候补排队机制，退票释放座位后自动为候补用户兑现
- 候补提交时冻结预付款（模拟预授权），兑现时扣款，取消/过期时解冻
- 用户取消候补的操作始终成功，不因并发匹配而失败
- DB 与 Redis 最终一致，30 秒内收敛

**Non-Goals:**
- 不支持多车次优先级候补（12306 最多 2 个备选车次，本项目仅支持单个车次）
- 不接入真实支付预授权 SDK，预付款冻结为模拟实现
- 不支持 WebSocket 实时推送候补状态变更（用户通过轮询查询）
- 不改造抢票流程（抢票失败仍直接 CANCELED，候补为独立入口）

## Decisions

### 1. 候补入口：独立接口 vs 抢票失败自动候补

**选择**: 独立入口 `/order/waitlist-create`

**理由**: 12306 的候补和预订是两个独立按钮，用户主动选择。自动候补会剥夺用户选择权（可能用户想换车次）。独立入口也避免了改造现有抢票流程的风险。

**替代方案**: 抢票失败后自动进入候补 — 实现更复杂，且用户可能不想候补同车次。

### 2. 预付款：模拟预授权冻结

**选择**: 方案 B — 候补时创建 `Pay(status=FROZEN)`

**理由**: 面试项目无需接入真实支付 SDK，但可以展示预授权的设计思路。Pay 表已有 status 字段，新增 FROZEN 状态即可。

**状态流转**:
```
提交候补 → Pay(FROZEN)
兑现成功 → Pay(FROZEN → SUCCESS)   CAS: UPDATE WHERE status='FROZEN'
取消/过期 → Pay(FROZEN → REFUNDED) CAS: UPDATE WHERE status='FROZEN'
超时未付 → Pay(SUCCESS → REFUNDED) 复用现有 cancelOrder 逻辑
```

### 3. 触发机制：事件驱动 + 定时兜底

**选择**: 混合模式

**事件驱动**: `cancelOrder` 释放座位后，立即检查该车次所有座位类型的候补队列，非空则调用 `processWaitlist`。延迟最低。

**定时兜底**: 
- `WaitlistMatchTask` (@Scheduled 30s): 从 DB 读 `status=WAITING` 记录，按 (trainId, seatType, route) 分组调用 `processWaitlist`。防止事件丢失导致候补饿死。
- `WaitlistExpireTask` (@Scheduled 60s): 从 DB 读 `expire_time < NOW()` 的 WAITING 记录，标记 EXPIRED + 退款 + ZREM Redis。

**替代方案**: 纯事件驱动 — 依赖 cancelOrder 触发，如果 Redis 消息丢失则候补永远不被处理。纯定时扫描 — 有最多 30s 延迟。混合模式兼顾实时性和可靠性。

### 4. 取消 vs 匹配冲突：cancelWaitlist 降级为 cancelOrder

**选择**: 取消操作始终成功

**机制**:
```
cancelWaitlist(waitlistSn):
  查 Waitlist → 拿到 orderSn
  status == WAITING:
    CAS: WAITING → CANCELED
    成功 → ZREM + 退款 (无锁座，快速路径)
    失败 → match 抢先了，fall through
  status != WAITING (MATCHED/EXPIRED/CANCELED):
    调用 cancelOrder(orderSn) → 释放座位 + 退款
```

**理由**: 用户取消意图是最高优先级，不应因系统内部状态流转而失败。CAS 保证了 match 和 cancel 的互斥，失败方走降级路径。

### 5. DB-Redis 一致性：DB 先写，Redis best-effort

**选择**: DB 为唯一真相源，Redis Sorted Set 仅做排序加速

**写入顺序**: DB INSERT → ZADD Redis（Redis 失败不重试，兜底任务补偿）
**读取顺序**: Redis ZPOPMIN → DB SELECT 校验状态 → DB CAS 最终裁决
**删除顺序**: DB CAS UPDATE → ZREM Redis（Redis 失败无害，队列多一条脏数据）

**理由**: 候补场景对实时性要求不高（秒级延迟可接受），但对可靠性要求高。DB 先写保证数据不丢，Redis 丢失由兜底任务 30s 内补偿。

### 6. 候补匹配的座位选择

**选择**: 复用 `SeatSelector.selectAndLockSeats`，但候补场景不支持选座偏好

**理由**: 候补用户接受"有座就行"，不需要智能选座算法。但仍需处理区间匹配（位图交集检查）和跨车厢选座。

**匹配失败处理**: 余票不足时将候补记录重新入队（ZADD 回 Redis），等待下次触发。

### 7. 候补区间与退票区间的匹配

**问题**: 用户 A 候补北京→济南，用户 B 退票北京→上海。退票释放的位图覆盖北京→济南的子区间吗？

**方案**: `processWaitlist` 中先查询可用车座（`(seat_bitmap & purchaseMask) = 0`），如果查到则说明区间可被满足。位图模型天然支持子区间检查，无需额外逻辑。

## Risks / Trade-offs

**[风险] 候补兑现后用户不支付**
→ 缓解: 15 分钟支付超时，超时后 cancelOrder 释放座位并触发下一位候补。座位浪费窗口最多 15 分钟。

**[风险] 高并发退票导致候补匹配风暴**
→ 缓解: `processWaitlist` 内部有分布式锁（复用选座锁），同一车次同一座位类型的匹配串行化。Redis ZPOPMIN 原子弹出避免重复消费。

**[风险] 候补队列过长导致匹配耗时**
→ 缓解: 匹配到第一个成功就停止（不是遍历全队列）。队列按时间排序，先到先得。

**[风险] Redis 重启导致队列丢失**
→ 缓解: 兜底任务 30s 从 DB 重建。期间有候补请求也不会丢失（DB 先写）。

**[权衡] 事件驱动增加了 cancelOrder 和候补服务的耦合**
→ 接受: cancelOrder 末尾调用 `waitlistService.triggerMatch(trainId)` 是轻量级调用（只查 Redis 队列是否非空），不影响现有取消流程的性能。
