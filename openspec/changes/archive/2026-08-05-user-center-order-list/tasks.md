## 1. 基础设施

- [x] 1.1 新建 `util/AesUtil.java` — AES/CBC/PKCS5Padding 加解密 + 身份证脱敏(前3后4)
- [x] 1.2 新建 `common/page/PageResponse.java` — 通用分页响应(total, current, size, records) + `from(IPage)` 工厂方法
- [x] 1.3 `application.yaml` 新增 `aes.secret-key` 配置项

## 2. 用户中心 — DTO

- [x] 2.1 新建 `dto/resp/UserRespDTO.java` — 个人信息响应(username, realName, phone脱敏, idType, idCard脱敏, mail, region, address, userType, verifyStatus, telephone, postCode)，不含 password
- [x] 2.2 新建 `dto/req/UserUpdateReqDTO.java` — 修改资料请求(realName, idType, idCard, mail, region, address, telephone, postCode, userType)，全部可选
- [x] 2.3 新建 `dto/req/ChangePasswordReqDTO.java` — 改密请求(oldPassword, newPassword, confirmPassword)

## 3. 用户中心 — 责任链

- [x] 3.1 新建 `handler/filter/UserUpdateChainFilter.java` — 标记接口(UserUpdateChainFilter extends AbstractChainFilter<UserUpdateReqDTO>)
- [x] 3.2 新建 `handler/filter/XxxChainHandler.java` (UserUpdateParamNotNullChainHandler, order=0) — 校验 reqDTO 非 null
- [x] 3.3 新建 `handler/filter/ChangePasswordChainFilter.java` — 标记接口
- [x] 3.4 新建 `handler/filter/XxxChainHandler.java` (ChangePasswordParamNotNullChainHandler, order=0) — 校验 oldPassword/newPassword/confirmPassword 非空 + newPassword==confirmPassword
- [x] 3.5 新建 `handler/filter/UserDeleteChainFilter.java` — 标记接口
- [x] 3.6 新建 `handler/filter/XxxChainHandler.java` (UserDeleteParamNotNullChainHandler, order=0) — 校验 password 非空

## 4. 用户中心 — Service + Controller

- [x] 4.1 新建 `service/UserService.java` — 接口: getUserProfile(), updateProfile(), changePassword(), deleteAccount()
- [x] 4.2 新建 `service/impl/UserServiceImpl.java` — 实现：查DB脱敏返回、部分更新、BCrypt改密、软删除注销
- [x] 4.3 新建 `controller/UserController.java` — GET /user/me, PUT /user/update, POST /user/change-password, POST /user/delete

## 5. 订单列表 — DTO

- [x] 5.1 新建 `dto/req/OrderListReqDTO.java` — 继承 PageRequest，增加 status/startDate/endDate/trainNumber 筛选字段
- [x] 5.2 新建 `dto/resp/OrderListRespDTO.java` — 订单列表项(orderSn, trainNumber, ridingDate, startStation, endStation, departureTime, arrivalTime, status, orderTime, totalAmount, passengerCount)

## 6. 订单列表 — 责任链

- [x] 6.1 新建 `handler/filter/OrderListChainFilter.java` — 标记接口
- [x] 6.2 新建 `handler/filter/XxxChainHandler.java` (OrderListParamVerifyChainHandler, order=0) — 校验 status 在枚举范围/日期格式/size<=50

## 7. 订单列表 — Service + Controller

- [x] 7.1 修改 `service/OrderService.java` — 新增 `PageResponse<OrderListRespDTO> listUserOrders(OrderListReqDTO reqDTO)` 方法签名
- [x] 7.2 修改 `service/impl/OrderServiceImpl.java` — 实现分页查询(LambdaQueryWrapper + selectPage) + 汇总金额/乘车人数
- [x] 7.3 修改 `controller/OrderController.java` — 新增 `GET /order/list` 端点

## 8. 数据库

- [x] 8.1 对 `t_order` 表新增 `(user_id, status, order_time)` 联合索引（手动执行 SQL，不放在 migrations 中）

## 9. 验证

- [x] 9.1 启动应用，测试 GET /user/me 返回个人信息（含脱敏）
- [x] 9.2 测试 PUT /user/update 修改资料后再次查询验证
- [x] 9.3 测试 POST /user/change-password 旧密码错误/成功/新密码不一致三个场景
- [x] 9.4 测试 POST /user/delete 注销后登录提示未注册
- [x] 9.5 测试 GET /order/list 无筛选/按状态/按日期/按车次/组合筛选五种场景
- [x] 9.6 测试 GET /order/list 分页边界（size>50 被拒绝）
