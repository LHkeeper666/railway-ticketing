## 1. Phase 1 — 基础设施 + 简单 CRUD（admin-auth, station, region）

### 1.1 角色体系

- [x] 1.1.1 `t_user` 表新增 `role` 列（TINYINT DEFAULT 0），更新 `db_table.sql`
- [x] 1.1.2 `User` 实体新增 `role` 字段
- [x] 1.1.3 `UserInfo` DTO 新增 `role` 字段
- [x] 1.1.4 `JwtUtil` 签发 token 时增加 `role` claim，解析时读取 `role`
- [x] 1.1.5 `JwtInterceptor` 解析 JWT 时将 `role` 设置到 `UserContext`
- [x] 1.1.6 新增 `@AdminRequired` 注解（`annotation/AdminRequired.java`）
- [x] 1.1.7 新增 `AdminInterceptor`（order=2，校验 role=1）
- [x] 1.1.8 `WebMvcConfig` 注册 `AdminInterceptor` 拦截 `/admin/**`
- [x] 1.1.9 `AuthServiceImpl.login` 校验用户状态（被禁用则拒绝登录）
- [x] 1.1.10 编写 admin-auth 单元测试：JWT 含 role、拦截器校验、禁用用户登录被拒

### 1.2 区域管理

- [x] 1.2.1 新增 `AdminRegionService` 接口 + `AdminRegionServiceImpl`
- [x] 1.2.2 新增 `AdminRegionController`（`/admin/region`）
- [x] 1.2.3 实现分页列表 `GET /admin/region/page`（关键字搜索）
- [x] 1.2.4 实现详情 `GET /admin/region/{id}`
- [x] 1.2.5 实现新增 `POST /admin/region`（含 Redis 缓存刷新）
- [x] 1.2.6 实现修改 `PUT /admin/region/{id}`（含 Redis 缓存刷新）
- [x] 1.2.7 实现删除 `DELETE /admin/region/{id}`（检查是否被 station/train 引用）
- [x] 1.2.8 编写 region CRUD 集成测试

### 1.3 站点管理

- [x] 1.3.1 新增 `AdminStationService` 接口 + `AdminStationServiceImpl`
- [x] 1.3.2 新增 `AdminStationController`（`/admin/station`）
- [x] 1.3.3 实现分页列表 `GET /admin/station/page`（关键字搜索）
- [x] 1.3.4 实现详情 `GET /admin/station/{id}`
- [x] 1.3.5 实现新增 `POST /admin/station`
- [x] 1.3.6 实现修改 `PUT /admin/station/{id}`
- [x] 1.3.7 实现删除 `DELETE /admin/station/{id}`（检查是否被 TrainStation 引用）
- [x] 1.3.8 编写 station CRUD 集成测试

## 2. Phase 2 — 核心模块（train-management + 路线变更安全 + price 废弃）

### 2.1 列车基本信息 CRUD

- [x] 2.1.1 新增 `AdminTrainService` 接口 + `AdminTrainServiceImpl`
- [x] 2.1.2 新增 `AdminTrainController`（`/admin/train`）
- [x] 2.1.3 实现分页列表 `GET /admin/train/page`（按车次号、类型筛选）
- [x] 2.1.4 实现详情 `GET /admin/train/{id}`（含路线、车厢、座位、价格汇总）
- [x] 2.1.5 实现新增 `POST /admin/train`（基本信息，sale_status=0）
- [x] 2.1.6 实现修改元数据 `PUT /admin/train/{id}`（校验：有活跃订单时拒绝时间变更）
- [x] 2.1.7 实现删除 `DELETE /admin/train/{id}`（无订单物理删除，有订单软删除）

### 2.2 路线配置与变更安全

- [x] 2.2.1 新增 `TrainStationChangeChecker` 辅助类（changeType 检测：APPEND/DELETE_END/INSERT_MIDDLE/DELETE_MIDDLE/METADATA_ONLY）
- [x] 2.2.2 实现 `PUT /admin/train/{id}/stations`（设置路线：无订单时全量替换）
- [x] 2.2.3 实现 `POST /admin/train/{id}/stations/append`（末尾追加，含活跃订单安全检查）
- [x] 2.2.4 实现 `POST /admin/train/{id}/stations/insert`（中间插入，有活跃订单时拒绝）
- [x] 2.2.5 实现 `DELETE /admin/train/{id}/stations/{stationId}`（删除停站，有活跃订单时中间站拒绝）
- [x] 2.2.6 编写 TrainStationChangeChecker 单元测试（覆盖所有变更类型组合）

### 2.3 TrainStationRelation 自动生成

- [x] 2.3.1 新增 `TrainStationRelationGenerator` 辅助类（根据有序站点列表生成全部 C(n,2) 关系）
- [x] 2.3.2 路线变更后自动调用 `TrainStationRelationGenerator.generate(trainId, stations)`
- [x] 2.3.3 编写 Relation 生成逻辑单元测试（验证 3站/5站 组合数量及字段正确性）

### 2.4 克隆列车

- [x] 2.4.1 实现 `POST /admin/train/{id}/clone`（接收 trainNumber + 可选的 stations/carriages）
- [x] 2.4.2 克隆 Train → 克隆/重置 Carriage → 克隆/重置 Seat（seat_bitmap=0, seat_status=AVAILABLE）
- [x] 2.4.3 按新路线生成 TrainStation → TrainStationRelation → TrainStationPrice
- [x] 2.4.4 冻结旧车：`UPDATE t_train SET sale_status=1 WHERE id=oldId`
- [x] 2.4.5 整个 clone 操作包裹 `@Transactional`，失败回滚
- [x] 2.4.6 编写 clone 集成测试（验证新旧车数据隔离、旧车 sale_status=1、新车 seat_bitmap 全为 0）

### 2.5 车厢与座位管理

- [x] 2.5.1 实现 `GET /admin/train/{id}/carriages`（车厢列表）
- [x] 2.5.2 实现 `POST /admin/train/{id}/carriage`（新增车厢 + 自动批量生成座位）
- [x] 2.5.3 实现 `DELETE /admin/train/{id}/carriage/{carriageId}`（删除车厢及关联座位）
- [x] 2.5.4 实现 `GET /admin/train/{id}/seats`（座位列表，按车厢分组）
- [x] 2.5.5 实现 `DELETE /admin/train/{id}/seat/{seatId}`（删除单个座位）
- [x] 2.5.6 编写车厢/座位管理集成测试

### 2.6 价格管理 + seat.price 废弃

- [x] 2.6.1 实现 `GET /admin/train/{id}/prices`（价格列表）
- [x] 2.6.2 实现 `PUT /admin/train/{id}/prices/batch`（批量设置区间价格，upsert 模式）
- [x] 2.6.3 `Seat` 实体 `price` 字段加 `@Deprecated` 注释标记废弃，DB 列不动
- [x] 2.6.4 新增票价时仅写 `t_train_station_price`，不再同步更新 `t_seat.price`
- [x] 2.6.5 编写价格管理集成测试

### 2.7 晚点接口

- [x] 2.7.1 实现 `POST /admin/train/{id}/delay`（接收 delayMinutes，正数晚点负数早点）
- [x] 2.7.2 自动更新 Train.departure_time / arrival_time
- [x] 2.7.3 自动更新所有 TrainStation.departure_time / arrival_time
- [x] 2.7.4 自动更新所有 TrainStationRelation.departure_time / arrival_time
- [x] 2.7.5 清除 Redis 缓存
- [x] 2.7.6 编写 delay 接口单元测试

## 3. Phase 3 — 订单与用户管理

### 3.1 订单管理

- [x] 3.1.1 新增 `AdminOrderService` 接口 + `AdminOrderServiceImpl`
- [x] 3.1.2 新增 `AdminOrderController`（`/admin/order`）
- [x] 3.1.3 实现分页列表 `GET /admin/order/page`（按状态、车次、用户ID、日期范围筛选）
- [x] 3.1.4 实现详情 `GET /admin/order/{orderSn}`（含 orderItem、ticket、pay 全部关联数据）
- [x] 3.1.5 实现手动取消 `POST /admin/order/{orderSn}/cancel`（复用现有 cancelOrder 逻辑）
- [x] 3.1.6 实现手动退票 `POST /admin/order/{orderSn}/refund`（复用现有 refund 逻辑）
- [x] 3.1.7 编写订单管理集成测试

### 3.2 用户管理

- [x] 3.2.1 新增 `AdminUserService` 接口 + `AdminUserServiceImpl`
- [x] 3.2.2 新增 `AdminUserController`（`/admin/user`）
- [x] 3.2.3 实现分页列表 `GET /admin/user/page`（关键字搜索、按 role 筛选）
- [x] 3.2.4 实现详情 `GET /admin/user/{id}`（身份证脱敏，密码不返回）
- [x] 3.2.5 实现状态管理 `PUT /admin/user/{id}/status`（启用/禁用）
- [x] 3.2.6 编写用户管理集成测试

## 4. 文档与收尾

- [x] 4.1 更新 `CLAUDE.md` 补充 admin 模块说明
- [x] 4.2 补充 `db_data.sql` 中至少一个 admin 用户种子数据（role=1）
- [x] 4.3 全链路测试：admin 登录 → 创建区域 → 创建站点 → 创建列车 → 配置路线 → 克隆列车 → 查询订单 → 查看用户
