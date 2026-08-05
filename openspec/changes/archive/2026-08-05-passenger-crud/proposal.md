## Why

当前 `t_passenger` 表、`Passenger` Entity 和 `PassengerMapper` 已存在，但缺少 Controller/Service/校验层。用户下单时依赖乘车人数据，而乘车人只能通过 DB 直接写入，缺少标准的 CRUD 入口。这是订票流程的核心业务闭环缺失环节，也是微服务化拆分中"用户服务"的基础能力。

## What Changes

- 新增 `PassengerController`，提供创建/修改/删除/列表四个 REST 端点
- 新增 `PassengerService` 接口 + `PassengerServiceImpl` 实现，统一乘车人业务逻辑
- 新增三条责任链（`PASSENGER_CREATE` / `PASSENGER_UPDATE` / `PASSENGER_DELETE`），规范化参数校验
- `t_passenger` 表新增 `user_id` 字段，以不可变主键关联用户，为微服务化做准备
- 响应 DTO 对身份证号和手机号做展示脱敏
- 现有代码中直接注入 `PassengerMapper` 的地方（`OrderRefundStatusChainHandler`）改为通过 `PassengerService` 调用

## Capabilities

### New Capabilities
- `passenger-crud`: 乘车人增删改查，含参数校验、归属校验、数量限制、脱敏展示、删除前置检查

### Modified Capabilities
<!-- None — this is a new capability, existing specs unchanged -->

## Impact

- 新增文件：6 个 DTO/Service/Controller + 6 个责任链类 = 12 个文件
- 修改文件：`Passenger.java`（加 userId）、`ChainMarkEnum.java`（加 3 个枚举值）、`db_table.sql`（加列）、`OrderRefundStatusChainHandler.java`（切换为 PassengerService 调用）
- 现有下单/退票/改签/候补流程中 `PassengerMapper.selectByIds()` 调用保持兼容（只读查询无需改动）
- 微服务化影响：`PassengerService` 接口可直接作为 Feign 契约，`userId` 作为跨服务乘客标识
