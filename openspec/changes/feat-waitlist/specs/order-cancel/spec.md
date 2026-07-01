## MODIFIED Requirements

### Requirement: 取消订单释放座位后触发候补
`cancelOrder` 在成功释放座位（seat_bitmap 清位 + 缓存失效）后，SHALL 检查该车次所有座位类型的候补队列，非空则调用 `processWaitlist` 为下一位候补用户分配座位。

#### Scenario: 取消有候补等待的订单
- **WHEN** 用户取消一个 UNPAID/PAID 订单，该车次对应座位类型存在 WAITING 状态的候补记录
- **THEN** `cancelOrder` 释放座位后立即调用 `waitlistService.triggerMatch(trainId, startStation, endStation)`，触发候补匹配

#### Scenario: 取消无候补等待的订单
- **WHEN** 用户取消订单，该车次无候补记录
- **THEN** `cancelOrder` 正常完成，不执行候补相关操作

#### Scenario: 取消 PENDING/WAITLIST 状态的订单
- **WHEN** 用户取消 PENDING 或 WAITLIST 状态的订单（无座位锁定）
- **THEN** 系统 SHALL 直接 CAS 更新状态为 CANCELED，不触发候补匹配（没有座位释放）

### Requirement: cancelOrder 支持 WAITLIST 状态
`cancelOrder` SHALL 支持取消 WAITLIST 状态的订单。当 CAS 成功时（match 未开始），走快速路径清理 Waitlist 记录和退款；当 CAS 失败时（match 已推进到 UNPAID），递归重试走标准取消流程。

#### Scenario: 取消 WAITLIST 状态的订单（match 未开始）
- **WHEN** Order 状态为 WAITLIST，match 尚未将其改为 UNPAID
- **THEN** CAS 更新 Order→CANCELED，清理 Waitlist 记录（ZREM + Pay→REFUNDED），不释放座位（未锁座）

#### Scenario: 取消 WAITLIST 状态的订单（match 已完成）
- **WHEN** Order 状态已被 match 改为 UNPAID
- **THEN** WAITLIST CAS 失败，系统递归调用 `cancelOrder`，重读状态为 UNPAID，走标准 UNPAID→CANCELED 流程释放座位
