## ADDED Requirements

### Requirement: 订单创建支持位置偏好
系统 SHALL 在订单创建接口支持可选的位置偏好参数。

#### Scenario: 传入位置偏好
- **WHEN** 用户调用订单创建接口，传入 chooseSeats = ["A", "B", "C"]
- **THEN** 系统将偏好传递给选座算法，优先分配 A、B、C 位置

#### Scenario: 不传位置偏好
- **WHEN** 用户调用订单创建接口，不传 chooseSeats 参数
- **THEN** 系统使用默认选座逻辑，随机分配座位

#### Scenario: 偏好数量校验
- **WHEN** 用户传入 chooseSeats 长度 > 乘客数量
- **THEN** 系统返回错误 "偏好数量不能超过乘客数量"

#### Scenario: 座位类型一致性校验
- **WHEN** 同一订单的乘客选择不同的座位类型
- **THEN** 系统返回错误 "同一订单只能选择同一种座位类型"

### Requirement: 请求参数扩展
系统 SHALL 扩展订单创建请求 DTO，支持位置偏好参数。

#### Scenario: OrderCreateReqDTO 结构
- **WHEN** 接收订单创建请求
- **THEN** 请求体包含 trainId, startStation, endStation, passengers, chooseSeats (可选)

#### Scenario: 向后兼容
- **WHEN** 旧版本客户端不传 chooseSeats
- **THEN** 系统正常处理，使用默认选座逻辑
