## ADDED Requirements

### Requirement: Create Passenger

系统 SHALL 允许已登录用户添加乘车人，乘车人信息包括真实姓名、证件类型、证件号码、手机号和优惠类型。用户最多添加 15 名乘车人。

#### Scenario: Successful creation
- **WHEN** 用户 POST /passenger 提供有效的 realName、idType=1（身份证）、idCard、phone
- **THEN** 系统创建乘车人，userId 设为当前用户 ID，返回 PassengerRespDTO 含雪花 ID

#### Scenario: Max passenger limit
- **WHEN** 用户已有 15 名乘车人，尝试创建第 16 名
- **THEN** 系统返回 400 错误 "每位用户最多添加15位乘车人"

#### Scenario: Duplicate id card under same user
- **WHEN** 用户尝试添加与已有乘车人证件号相同的乘车人
- **THEN** 系统返回 400 错误 "乘车人已存在"

#### Scenario: Missing required fields
- **WHEN** 请求缺少 realName、idType、idCard 或 phone
- **THEN** 系统返回 400 错误，指出缺失字段

#### Scenario: Invalid phone format
- **WHEN** 请求 phone 不符合手机号格式 `^1[3-9]\d{9}$`
- **THEN** 系统返回 400 错误 "手机号格式不正确"

#### Scenario: Invalid id type
- **WHEN** 请求 idType 不是有效枚举值（1=身份证, 2=护照 等）
- **THEN** 系统返回 400 错误 "证件类型无效"

### Requirement: Update Passenger

系统 SHALL 允许用户修改本人名下乘车人的手机号和优惠类型。证件信息（姓名、证件类型、证件号码）不可修改。

#### Scenario: Successful phone update
- **WHEN** 用户 PUT /passenger/{id} 提供有效的新手机号
- **THEN** 系统更新乘车人手机号，返回更新后的 PassengerRespDTO

#### Scenario: Attempt to modify other user's passenger
- **WHEN** 用户尝试修改不属于自己的乘车人
- **THEN** 系统返回 400 错误 "乘车人不存在或无权操作"

#### Scenario: Passenger not found
- **WHEN** 用户 PUT 不存在的乘客 ID
- **THEN** 系统返回 400 错误 "乘车人不存在"

### Requirement: Delete Passenger

系统 SHALL 允许用户删除本人名下的乘车人。删除为软删除。若该乘车人存在未完成的订单（状态为 PAID 或 UNPAID），不允许删除。

#### Scenario: Successful deletion
- **WHEN** 用户 DELETE /passenger/{id}，且该乘车人无进行中订单
- **THEN** 系统软删除乘车人（delFlag=1），返回成功

#### Scenario: Passenger has active orders
- **WHEN** 用户尝试删除有未完成订单的乘车人
- **THEN** 系统返回 400 错误 "该乘车人有未完成的订单，无法删除"

#### Scenario: Attempt to delete other user's passenger
- **WHEN** 用户尝试删除不属于自己的乘车人
- **THEN** 系统返回 400 错误 "乘车人不存在或无权操作"

### Requirement: List My Passengers

系统 SHALL 允许用户查询自己名下的全部乘车人，按添加时间排序，最多返回 15 条。响应中证件号和手机号做脱敏处理。

#### Scenario: User with passengers
- **WHEN** 用户 GET /passenger/list
- **THEN** 系统返回该用户全部乘车人列表，idCard 格式为 `320***********1234`，phone 格式为 `138****5678`

#### Scenario: User with no passengers
- **WHEN** 用户 GET /passenger/list，且该用户未添加任何乘车人
- **THEN** 系统返回空列表 `[]`

#### Scenario: Unauthenticated access
- **WHEN** 请求未携带有效的 Authorization header
- **THEN** 系统返回 401（拦截器层面处理，与现有认证机制一致）
