## Why

当前系统只有登录注册两个用户相关接口，缺少用户中心的核心功能（查看/修改个人信息、修改密码、注销账号），面试时会被追问"用户体系怎么设计的"。同时订单查询只有单个详情接口，缺少按用户/状态/时间的分页列表查询，用户无法看到"我的订单"。这两个功能是核心业务闭环的基础，完成后整个 C 端用户体验链路就完整了。

## What Changes

- 新增 **用户中心** 模块：查看个人信息、修改资料、修改密码、注销账号四个接口
- 新增 **订单列表查询**：按状态/日期/车次筛选的分页查询接口
- 新增通用分页响应 `PageResponse<T>`，供后续列表类接口复用
- 身份证号采用 AES 加密存储，响应中做脱敏处理（只显示前 3 后 4）

## Capabilities

### New Capabilities
- `user-center`: 用户个人中心 — 个人信息查看/修改、密码修改、账号注销（软删除）
- `order-list`: 订单列表分页查询 — 按用户、状态、日期范围、车次号筛选，分页返回

### Modified Capabilities
<!-- None — this is an additive change, no existing capability requirements are modified -->

## Impact

- **新增文件**: `UserController`, `UserService`/`UserServiceImpl`, `UserRespDTO`, `UserUpdateReqDTO`, `ChangePasswordReqDTO`, `OrderListReqDTO`, `OrderListRespDTO`, `PageResponse`, 6 个责任链 Handler, `AesUtil`
- **修改文件**: `OrderController`（新增 `/order/list` 端点）, `OrderService`（新增 `listUserOrders` 方法签名）, `OrderServiceImpl`
- **配置文件**: `application.yaml` 新增 `aes.secret-key` 配置项
- **数据库**: 建议对 `t_order` 表新增 `(user_id, status, order_time)` 联合索引
- **依赖**: 无新增第三方依赖（AES 使用 JDK 自带 `javax.crypto`）
