## Why

当前选座算法只支持按座位类型（商务座/一等座/二等座）选择，不支持用户选择具体位置（A/B/C/D/F），且同一订单的多个乘客座位是随机分配的，无法保证座位相邻。参考 12306 的设计，需要实现智能选座功能，让用户可以选择位置偏好，系统优先分配同一排连续座位，提升用户体验。

## What Changes

- 新增位置偏好参数 `chooseSeats`，支持用户选择 A/B/C/D/F 位置
- 实现智能选座算法，优先级：同排+偏好 → 同排连续 → 同排分散 → 跨车厢
- 新增分布式锁保护选座过程，保证并发安全
- 新增参数校验：座位类型一致性、偏好数量合理性、偏好字符有效性
- 支持跨车厢选座，当单车厢无法满足时自动降级到多车厢分配

## Capabilities

### New Capabilities
- `smart-seat-selection`: 智能选座算法，支持位置偏好、连续座位分配、跨车厢降级
- `seat-matrix-builder`: 座位矩阵构建，从数据库查询结果构建内存矩阵用于选座算法
- `distributed-seat-lock`: 分布式锁保护选座过程，按列车+座位类型粒度加锁

### Modified Capabilities
- `order-creation`: 订单创建接口新增 chooseSeats 参数，支持位置偏好传入

## Impact

- **代码变更**：
  - `SeatSelector.java`: 重构选座逻辑，新增智能选座算法
  - `OrderCreateReqDTO.java`: 新增 chooseSeats 字段
  - 新增 `SeatMatrixDTO.java`, `SelectedSeatDTO.java`, `CarriageInfo.java`, `SeatNumberParser.java`
  - 新增责任链校验器：座位类型一致性校验、偏好参数校验

- **API 变更**：
  - `POST /order/create` 和 `POST /order/flash-create` 请求体新增 `chooseSeats` 字段（可选）

- **数据库**：无结构变更

- **依赖**：无新增依赖
