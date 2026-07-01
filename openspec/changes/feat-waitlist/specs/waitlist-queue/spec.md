## ADDED Requirements

### Requirement: Redis 候补队列管理
系统 SHALL 使用 Redis Sorted Set 维护候补队列，key 格式为 `waitlist:queue:{trainId}:{seatType}:{startStation}:{endStation}`，score 为创建时间戳（毫秒），member 为 waitlist_sn。

#### Scenario: 候补入队
- **WHEN** 用户成功提交候补订单
- **THEN** 系统执行 `ZADD` 将 waitlist_sn 加入对应车次-座位类型-区间的 Redis 队列，score 为当前时间戳

#### Scenario: 候补出队（匹配成功）
- **WHEN** 候补匹配成功
- **THEN** 系统执行 `ZREM` 从 Redis 队列中移除该 waitlist_sn

#### Scenario: 候补出队（取消/过期）
- **WHEN** 候补被取消或过期
- **THEN** 系统执行 `ZREM` 从 Redis 队列中移除该 waitlist_sn

### Requirement: DB-Redis 最终一致性
系统 SHALL 以 DB 为唯一真相源，Redis 仅做排序加速。DB 写入先于 Redis 操作，Redis 失败不重试，由定时兜底任务补偿。

#### Scenario: Redis 写入失败
- **WHEN** DB INSERT 成功但 ZADD Redis 失败
- **THEN** 系统 SHALL 不重试 Redis 操作，候补记录仅存在于 DB 中，兜底任务将在 30 秒内从 DB 读取并处理

#### Scenario: Redis 删除失败
- **WHEN** DB CAS 更新成功但 ZREM Redis 失败
- **THEN** Redis 队列中保留该 waitlist_sn（脏数据），匹配时从 DB 校验状态后 SKIP

#### Scenario: Redis 数据与 DB 不一致
- **WHEN** Redis 队列中的 waitlist_sn 在 DB 中状态不是 WAITING
- **THEN** 匹配流程 SHALL 从 DB 校验状态，非 WAITING 的记录直接 SKIP

### Requirement: 事件驱动候补触发
系统 SHALL 在 `cancelOrder` 释放座位后立即检查该车次的候补队列，非空则触发匹配。

#### Scenario: 取消订单后触发候补
- **WHEN** `cancelOrder` 成功释放座位（seat_bitmap 清位 + 缓存失效）
- **THEN** 系统遍历该车次所有座位类型的候补队列 key，非空则调用 `processWaitlist(trainId, seatType, startStation, endStation)`

#### Scenario: 候补队列为空
- **WHEN** `cancelOrder` 释放座位后该车次无候补记录
- **THEN** 系统 SHALL 跳过，不执行额外操作

### Requirement: 定时兜底匹配任务
系统 SHALL 提供 `WaitlistMatchTask`，每 30 秒从 DB 扫描 WAITING 状态的候补记录，按 (trainId, seatType, startStation, endStation) 分组调用 `processWaitlist`。

#### Scenario: 兜底任务发现未匹配的候补
- **WHEN** DB 中存在 WAITING 状态的候补记录
- **THEN** 系统按维度分组后逐组调用 `processWaitlist`，尝试为候补用户匹配座位

#### Scenario: 兜底任务与事件驱动并发
- **WHEN** 事件驱动和定时任务同时触发同一车次的 `processWaitlist`
- **THEN** 分布式锁保证同一维度的匹配串行化，不产生重复分配

### Requirement: 定时过期清理任务
系统 SHALL 提供 `WaitlistExpireTask`，每 60 秒从 DB 扫描 expire_time < NOW() 的 WAITING 记录，标记 EXPIRED 并退款。

#### Scenario: 过期清理
- **WHEN** Waitlist 状态为 WAITING 且 expire_time < NOW()
- **THEN** 系统更新 Waitlist→EXPIRED，Order→CANCELED，Pay: FROZEN→REFUNDED，并 ZREM Redis 队列

#### Scenario: 过期清理与匹配并发
- **WHEN** 过期清理和匹配同时处理同一候补记录
- **THEN** DB CAS 保证只有一个操作能赢

### Requirement: 候补匹配幂等性
`processWaitlist` 对同一候补记录的多次调用 SHALL 是幂等的。已处理的候补记录 SHALL 被跳过。

#### Scenario: 重复处理同一候补
- **WHEN** `processWaitlist` 弹出的 waitlist_sn 在 DB 中状态不是 WAITING
- **THEN** 系统 SHALL 跳过该记录，继续处理队列中的下一个
