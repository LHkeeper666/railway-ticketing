## ADDED Requirements

### Requirement: 座位矩阵构建
系统 SHALL 能够从数据库查询结果构建内存座位矩阵，用于选座算法。

#### Scenario: 构建二等座矩阵
- **WHEN** 查询到二等座座位列表 (如 18排 x 5列)
- **THEN** 系统构建 int[18][5] 矩阵，1 表示可用，0 表示已占

#### Scenario: 动态确定维度
- **WHEN** 座位数据包含不同排数和位置
- **THEN** 系统自动扫描数据，确定最大排号和所有位置类型

#### Scenario: 保留座位号映射
- **WHEN** 构建矩阵
- **THEN** 系统同时构建 seatNumberMap，用于将矩阵坐标转换回座位号

### Requirement: 位置索引映射
系统 SHALL 建立位置字符与列索引的映射关系。

#### Scenario: 二等座位置映射
- **WHEN** 二等座包含位置 A、B、C、D、F
- **THEN** 系统建立映射 {A→0, B→1, C→2, D→3, F→4}

#### Scenario: 一等座位置映射
- **WHEN** 一等座包含位置 A、C、D、F
- **THEN** 系统建立映射 {A→0, C→1, D→2, F→3}

#### Scenario: 商务座位置映射
- **WHEN** 商务座包含位置 A、C、F
- **THEN** 系统建立映射 {A→0, C→1, F→2}

### Requirement: 按车厢分组
系统 SHALL 将座位按车厢分组，构建每个车厢的独立矩阵。

#### Scenario: 多车厢数据
- **WHEN** 查询到 05 车厢和 06 车厢的座位
- **THEN** 系统分别构建 05 车厢矩阵和 06 车厢矩阵

#### Scenario: 车厢可用座位统计
- **WHEN** 构建车厢矩阵
- **THEN** 系统同时统计每个车厢的可用座位数
