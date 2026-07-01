## ADDED Requirements

### Requirement: 用户提交候补订单
系统 SHALL 提供 `POST /order/waitlist-create` 接口，允许用户在余票为 0 时提交候补订单。提交时系统 SHALL 创建 Order(status=WAITLIST)、Pay(status=FROZEN)、Waitlist 记录，并将候补加入 Redis 队列。

#### Scenario: 成功提交候补
- **WHEN** 用户携带有效 JWT 调用 `/order/waitlist-create`，传入 trainId、startStation、endStation、seatType、passengers，且该座位类型余票为 0，用户无重复候补
- **THEN** 系统返回 waitlistSn，Order 状态为 WAITLIST，Pay 状态为 FROZEN，Redis 队列中包含该候补记录

#### Scenario: 该座位类型仍有余票
- **WHEN** 用户提交候补，但该车次该座位类型余票 > 0
- **THEN** 系统 SHALL 拒绝候补请求，返回提示"该座位类型有余票，请直接预订"

#### Scenario: 同一车次区间重复候补
- **WHEN** 用户对同一车次同一出发站-到达站已有 WAITING 状态的候补订单
- **THEN** 系统 SHALL 拒绝请求，返回提示"您已有候补订单，请勿重复提交"

#### Scenario: 同一车次区间已有其他有效订单
- **WHEN** 用户对同一车次同一区间已有 UNPAID/PAID/PENDING 状态的订单
- **THEN** 系统 SHALL 拒绝请求，返回提示"您已有该区间的有效订单"

### Requirement: 候补订单兑现
系统 SHALL 在有座位释放时自动为候补用户匹配座位。匹配成功后系统 SHALL 将 Order 状态更新为 UNPAID，Pay 状态从 FROZEN 更新为 SUCCESS，生成 OrderItem 和 Ticket，并发送支付超时消息。

#### Scenario: 匹配成功
- **WHEN** 候补用户被选中且 `SeatSelector.selectAndLockSeats` 成功锁座
- **THEN** Waitlist 状态变为 MATCHED，Order 状态变为 UNPAID，Pay 状态变为 SUCCESS，系统发送 15 分钟支付超时消息

#### Scenario: 匹配时座位不足
- **WHEN** 候补用户被选中但可用车座数不足
- **THEN** 系统 SHALL 将候补记录重新入队，等待下次触发

#### Scenario: 匹配时候补已过期
- **WHEN** 候补用户被选中但当前时间已超过 expire_time
- **THEN** 系统 SHALL 标记 Waitlist 为 EXPIRED，Order 为 CANCELED，Pay 为 REFUNDED，并从 Redis 队列移除

### Requirement: 用户查询候补状态
系统 SHALL 提供 `GET /order/waitlist/{waitlistSn}` 接口，返回候补订单的当前状态、排队位置、截止时间等信息。

#### Scenario: 查询进行中的候补
- **WHEN** 用户查询 WAITING 状态的候补订单
- **THEN** 系统返回候补状态、队列位置（ZRANK）、预计截止时间、候补乘客信息

#### Scenario: 查询已兑现的候补
- **WHEN** 用户查询 MATCHED 状态的候补订单
- **THEN** 系统返回已兑现状态及关联的订单详情（座位号、支付状态等）

### Requirement: 用户取消候补
系统 SHALL 提供 `POST /order/waitlist/{waitlistSn}/cancel` 接口。取消操作 SHALL 始终成功，不因并发匹配而失败。

#### Scenario: 取消 WAITING 状态的候补
- **WHEN** 用户取消 WAITING 状态的候补订单
- **THEN** 系统 CAS 更新 Waitlist 为 CANCELED，Order 为 CANCELED，Pay 为 REFUNDED，从 Redis 队列移除

#### Scenario: 取消已 MATCHED 的候补
- **WHEN** 用户取消已 MATCHED 的候补订单（match 已锁座）
- **THEN** 系统 SHALL 降级调用 `cancelOrder`，释放座位、退款、触发下一位候补

#### Scenario: 并发取消与匹配
- **WHEN** 用户发起取消的同时系统正在匹配该候补
- **THEN** DB CAS 保证只有一个操作能赢。若取消 CAS 失败，系统降级调用 `cancelOrder`；若匹配 CAS 失败，匹配跳过。用户始终感知"取消成功"

### Requirement: 候补支付超时
候补兑现后用户未在 15 分钟内完成确认的，系统 SHALL 自动取消订单并释放座位。

#### Scenario: 兑现后超时未确认
- **WHEN** 候补订单已兑现（Order=UNPAID）且 15 分钟内用户未操作
- **THEN** 系统调用 `cancelOrder` 释放座位，Pay 状态变为 REFUNDED，触发下一位候补

### Requirement: 候补过期清理
系统 SHALL 在候补截止时间到达后自动取消未兑现的候补订单。

#### Scenario: 候补过期
- **WHEN** Waitlist 状态为 WAITING 且 expire_time 已过
- **THEN** 系统标记 Waitlist 为 EXPIRED，Order 为 CANCELED，Pay 为 REFUNDED，从 Redis 队列移除

### Requirement: 候补预付款冻结
系统 SHALL 在候补提交时创建 Pay(status=FROZEN) 记录，金额为该区间座位类型票价 × 候补人数。

#### Scenario: 提交候补时冻结
- **WHEN** 用户成功提交候补订单
- **THEN** 系统创建 Pay 记录，status=FROZEN，amount=票价×人数

#### Scenario: 兑现时扣款
- **WHEN** 候补匹配成功
- **THEN** 系统 CAS 更新 Pay: FROZEN → SUCCESS

#### Scenario: 取消时解冻
- **WHEN** 候补被取消（用户主动或过期）
- **THEN** 系统 CAS 更新 Pay: FROZEN → REFUNDED
