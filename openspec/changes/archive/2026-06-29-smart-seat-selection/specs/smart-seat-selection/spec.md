## ADDED Requirements

### Requirement: 智能选座算法
系统 SHALL 支持智能选座算法，根据用户偏好和可用座位情况，优先分配同一排连续座位。

#### Scenario: 同排满足偏好
- **WHEN** 用户选择偏好 ["A", "B", "C"]，且存在某排 A、B、C 位置都可用
- **THEN** 系统分配该排的 A、B、C 座位

#### Scenario: 同排连续座位（偏好降级）
- **WHEN** 用户选择偏好 ["A", "B", "C"]，但无法同时满足偏好
- **THEN** 系统在同一排找连续 3 个可用座位分配

#### Scenario: 同排分散座位（连续降级）
- **WHEN** 无法找到同排连续座位
- **THEN** 系统在同一排找任意可用座位分配

#### Scenario: 跨车厢降级
- **WHEN** 单车厢无法满足所有乘客
- **THEN** 系统在多个车厢分配座位，优先填满一个车厢再分配下一个

### Requirement: 位置偏好支持
系统 SHALL 支持用户选择位置偏好 (A/B/C/D/F)，并优先满足偏好。

#### Scenario: 有效偏好输入
- **WHEN** 用户传入 chooseSeats = ["A", "B", "C"]
- **THEN** 系统优先分配 A、B、C 位置的座位

#### Scenario: 偏好数量小于乘客数
- **WHEN** 3 个乘客，用户只选择偏好 ["A", "B"]
- **THEN** 系统为前 2 个乘客分配 A、B 位置，第 3 个乘客自动分配任意位置

#### Scenario: 无效偏好字符
- **WHEN** 用户传入 chooseSeats = ["A", "X"]，其中 X 无效
- **THEN** 系统返回错误 "无效的位置偏好"

### Requirement: 座位号解析
系统 SHALL 能够解析座位号，提取排号和位置信息。

#### Scenario: 解析标准座位号
- **WHEN** 输入座位号 "01A"
- **THEN** 解析结果为 row=1, position='A'

#### Scenario: 解析多位排号
- **WHEN** 输入座位号 "12F"
- **THEN** 解析结果为 row=12, position='F'

### Requirement: 跨车厢选座
系统 SHALL 支持跨车厢选座，当单车厢无法满足时自动降级。

#### Scenario: 单车厢满足
- **WHEN** 某车厢可用座位数 >= 乘客数
- **THEN** 系统在该车厢内完成选座

#### Scenario: 跨车厢分配
- **WHEN** 单车厢可用座位数 < 乘客数
- **THEN** 系统按车厢可用座位数降序分配，优先填满一个车厢

#### Scenario: 余票不足
- **WHEN** 所有车厢可用座位总数 < 乘客数
- **THEN** 系统返回错误 "余票不足"
