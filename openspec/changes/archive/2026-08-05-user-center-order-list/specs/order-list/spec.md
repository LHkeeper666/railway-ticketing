# order-list

## Purpose

订单列表分页查询，允许用户查看自己的订单历史，支持按状态、日期范围、车次号筛选。

## ADDED Requirements

### Requirement: List User Orders with Pagination

系统 SHALL 允许已登录用户分页查询自己的订单列表，支持按订单状态、乘车日期范围、车次号筛选。结果按订票时间倒序排列。

#### Scenario: Successful list query without filters
- **WHEN** 用户 GET /order/list?current=1&size=10 携带有效 JWT，无筛选条件
- **THEN** 系统返回该用户全部订单的分页结果，按 orderTime 倒序，包含 total、current、size、records

#### Scenario: Filter by status
- **WHEN** 用户 GET /order/list?current=1&size=10&status=1（PAID）
- **THEN** 系统仅返回状态为 PAID 的订单

#### Scenario: Filter by date range
- **WHEN** 用户 GET /order/list?current=1&size=10&startDate=2026-01-01&endDate=2026-06-30
- **THEN** 系统仅返回 ridingDate 在指定范围内的订单

#### Scenario: Filter by train number
- **WHEN** 用户 GET /order/list?current=1&size=10&trainNumber=G123
- **THEN** 系统返回 trainNumber 包含 "G123" 的订单（模糊匹配）

#### Scenario: Combined filters
- **WHEN** 用户同时提供 status、日期范围和车次号筛选条件
- **THEN** 系统返回同时满足所有条件的订单

#### Scenario: Empty result
- **WHEN** 用户的筛选条件匹配不到任何订单
- **THEN** 系统返回 total=0, records=[]

#### Scenario: Page size limit
- **WHEN** 用户请求的 size 超过 50
- **THEN** 系统通过责任链返回 400 错误 "每页最多查询50条"

#### Scenario: Invalid status value
- **WHEN** 用户提供不在 OrderStatusEnum 范围内的 status 值
- **THEN** 系统通过责任链返回 400 错误 "订单状态值无效"

#### Scenario: Invalid date format
- **WHEN** 用户提供的 startDate 或 endDate 不符合 yyyy-MM-dd 格式
- **THEN** 系统通过责任链返回 400 错误 "日期格式不正确"

#### Scenario: Unauthenticated access
- **WHEN** 请求未携带有效的 Authorization header
- **THEN** 系统返回 401（拦截器层面处理）

### Requirement: Order List Response Format

系统 SHALL 以简洁格式返回订单列表，单条记录 OrderListRespDTO 包含订单概要信息，不含完整的 OrderItem 列表和 PayInfo 详情。

#### Scenario: List item fields
- **WHEN** 系统返回订单列表
- **THEN** 每条记录包含: orderSn、trainNumber、ridingDate、startStation、endStation、departureTime、arrivalTime、status、orderTime、totalAmount（汇总金额）、passengerCount（乘车人数）
- **AND** 记录不包含 orderItems 详细列表和 payInfo

#### Scenario: Total amount calculation
- **WHEN** 订单有多个 OrderItem
- **THEN** totalAmount 为该订单所有 OrderItem 的 amount 之和

### Requirement: Common Page Response

系统 SHALL 提供通用分页响应结构 `PageResponse<T>`，位于 `common/page/` 包下，供所有列表类接口复用。

#### Scenario: Page response structure
- **WHEN** 系统返回分页结果
- **THEN** 响应 data 中包含 total（总条数）、current（当前页）、size（每页条数）、records（数据列表）
