# user-center

## Purpose

用户个人中心，提供个人信息查看/修改、密码修改、账号注销功能。身份证号采用 AES 加密存储，对外输出脱敏。

## ADDED Requirements

### Requirement: View Current User Profile

系统 SHALL 允许已登录用户查看自己的个人信息。响应中 idCard 和 phone 做脱敏处理，password 不返回。

#### Scenario: Successful profile retrieval
- **WHEN** 用户 GET /user/me 携带有效 JWT
- **THEN** 系统返回 UserRespDTO，包含 username、realName、phone（脱敏）、idType、idCard（脱敏）、mail、region、address、userType、verifyStatus
- **AND** password 字段不在响应中

#### Scenario: Unauthenticated access
- **WHEN** 请求未携带有效的 Authorization header
- **THEN** 系统返回 401（拦截器层面处理，与现有认证机制一致）

### Requirement: Update User Profile

系统 SHALL 允许已登录用户修改个人信息中的非敏感字段：realName、idType、idCard、mail、region、address、telephone、postCode、userType。修改后 JWT 令牌中的信息不受影响（JWT 为无状态签发）。

#### Scenario: Successful profile update
- **WHEN** 用户 PUT /user/update 提供有效的个人信息字段
- **THEN** 系统更新对应字段，idCard 以 AES 加密后存储，返回更新后的 UserRespDTO

#### Scenario: Id card encryption on update
- **WHEN** 用户提供新的 idCard
- **THEN** 系统将 idCard AES 加密后写入数据库，响应中返回脱敏格式 `320***********1234`

#### Scenario: Partial update
- **WHEN** 用户只提供部分字段（如只更新 mail）
- **THEN** 系统仅更新提供的非 null 字段，其余字段保持不变

#### Scenario: Missing required fields validation
- **WHEN** 请求参数不符合校验规则
- **THEN** 系统通过责任链返回 400 错误，指出具体缺失字段

### Requirement: Change Password

系统 SHALL 允许已登录用户修改登录密码。需提供旧密码校验身份，新密码需二次确认。

#### Scenario: Successful password change
- **WHEN** 用户 POST /user/change-password，旧密码正确，新密码与确认密码一致
- **THEN** 系统使用 BCrypt 加密新密码后更新数据库，返回成功

#### Scenario: Wrong old password
- **WHEN** 用户提供的旧密码与数据库中的哈希不匹配
- **THEN** 系统返回 400 错误 "旧密码错误"

#### Scenario: New password mismatch
- **WHEN** 用户提供的新密码与确认密码不一致
- **THEN** 系统通过责任链返回 400 错误 "两次输入的密码不一致"

#### Scenario: Missing fields
- **WHEN** 请求缺少 oldPassword、newPassword 或 confirmPassword
- **THEN** 系统通过责任链返回 400 错误

### Requirement: Delete Account

系统 SHALL 允许已登录用户注销账号。注销为软删除，需提供密码确认身份。注销后该用户的 JWT 在有效期内仍可使用（JWT 无状态的固有限制）。

#### Scenario: Successful account deletion
- **WHEN** 用户 POST /user/delete，密码正确
- **THEN** 系统设置 delFlag=1，记录 deletionTime 为当前时间戳，返回成功

#### Scenario: Wrong password
- **WHEN** 用户提供的密码与数据库中的哈希不匹配
- **THEN** 系统返回 400 错误 "密码错误"

#### Scenario: Already deleted
- **WHEN** 已注销的用户尝试任何操作
- **THEN** 系统通过 @TableLogic 自动过滤已删除记录，登录时返回 "手机号未注册"
