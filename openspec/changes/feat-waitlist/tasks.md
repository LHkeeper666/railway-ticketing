## 1. 数据模型与基础设施

- [x] 1.1 新增 `WaitlistStatusEnum` 枚举：WAITING(0), MATCHED(1), EXPIRED(2), CANCELED(3)
- [x] 1.2 新增 `OrderStatusEnum.WAITLIST(4)` 枚举值
- [x] 1.3 新增 `Waitlist` 实体类，映射 `t_waitlist` 表
- [x] 1.4 新增 `WaitlistPassenger` 实体类，映射 `t_waitlist_passenger` 表
- [x] 1.5 新增 `WaitlistMapper` 和 `WaitlistPassengerMapper` 接口
- [x] 1.6 `RedisConstant` 新增候补队列 key 常量 `WAITLIST_QUEUE_PREFIX`、`WAITLIST_LOCK_PREFIX`
- [x] 1.7 编写数据库建表 SQL（`t_waitlist`、`t_waitlist_passenger`）

## 2. DTO 层

- [x] 2.1 新增 `WaitlistCreateReqDTO`（trainId, startStation, endStation, seatType, passengers）
- [x] 2.2 新增 `WaitlistCreateRespDTO`（waitlistSn, message）
- [x] 2.3 新增 `WaitlistDetailRespDTO`（waitlistSn, status, queuePosition, expireTime, passengers, orderDetail）

## 3. 责任链校验

- [x] 3.1 新增 `WaitlistCreateChainFilter` 标记接口
- [x] 3.2 新增 `WaitlistCreateChainHandler`：参数非空校验、乘客信息校验、余票为 0 校验、无重复候补/有效订单校验
- [x] 3.3 `ChainMarkEnum` 新增 `WAITLIST_CREATE` 枚举值
- [x] 3.4 `WaitlistServiceImpl` 注入 `AbstractChainContext<WaitlistCreateReqDTO>`

## 4. 核心服务层 — WaitlistService

- [x] 4.1 新增 `WaitlistService` 接口：`createWaitlist`、`processWaitlist`、`cancelWaitlist`、`getWaitlistDetail`、`triggerMatch`
- [x] 4.2 `WaitlistServiceImpl.createWaitlist`：责任链校验 → 创建 Order(WAITLIST) → 创建 Pay(FROZEN) → 创建 Waitlist + WaitlistPassenger → ZADD Redis 队列
- [x] 4.3 `WaitlistServiceImpl.processWaitlist`：Redis ZRANGE 取候选 → DB 校验 WAITING → CAS WAITING→MATCHED → 选座锁座 → 成功: Order→UNPAID, Pay→SUCCESS, 写 OrderItem/Ticket, 发超时消息; 失败: 重新入队
- [x] 4.4 `WaitlistServiceImpl.cancelWaitlist`：查 Waitlist 拿 orderSn → status=WAITING 时 CAS→CANCELED+ZREM+退款; CAS 失败或 status!=WAITING 时降级调用 cancelOrder
- [x] 4.5 `WaitlistServiceImpl.getWaitlistDetail`：查询候补详情（状态、队列位置、乘客信息）
- [x] 4.6 `WaitlistServiceImpl.triggerMatch`：遍历该车次所有座位类型的候补队列 key，非空则调用 processWaitlist

## 5. 修改现有取消流程

- [x] 5.1 `OrderServiceImpl.cancelOrder` 新增 WAITLIST 快速路径：CAS WAITLIST→CANCELED，成功则清理 Waitlist+退款，失败则递归重试
- [x] 5.2 `OrderServiceImpl.cancelOrder` 在座位释放（seat_bitmap 清位 + 缓存失效）后调用 `waitlistService.triggerMatch`

## 6. Controller 层

- [x] 6.1 `OrderController` 新增 `POST /order/waitlist-create` 接口，调用 `waitlistService.createWaitlist`，加 `@RateLimit`
- [x] 6.2 `OrderController` 新增 `GET /order/waitlist/{waitlistSn}` 接口，调用 `waitlistService.getWaitlistDetail`
- [x] 6.3 `OrderController` 新增 `POST /order/waitlist/{waitlistSn}/cancel` 接口，调用 `waitlistService.cancelWaitlist`

## 7. 定时任务

- [x] 7.1 新增 `WaitlistMatchTask`：@Scheduled(fixedDelay=30s)，从 DB 读 WAITING 记录，按维度分组调用 processWaitlist，加 Redis 分布式锁防并发
- [x] 7.2 新增 `WaitlistExpireTask`：@Scheduled(fixedDelay=60s)，从 DB 读 expire_time < NOW() 的 WAITING 记录，CAS 更新 EXPIRED + 退款 + ZREM Redis

## 8. 验证与测试

- [ ] 8.1 启动应用，验证建表 SQL 执行成功
- [ ] 8.2 测试候补提交流程：POST /order/waitlist-create，验证 Order/Waitlist/Pay 记录和 Redis 队列
- [ ] 8.3 测试候补匹配流程：取消一个有候补等待的订单，验证候补自动兑现
- [ ] 8.4 测试取消候补流程：取消 WAITING 和 MATCHED 状态的候补，验证退款和座位释放
- [ ] 8.5 测试过期清理：等待或手动设置过期时间，验证自动清理
