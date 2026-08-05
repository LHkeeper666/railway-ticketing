## 1. Schema & Entity

- [x] 1.1 `db_table.sql`: `t_passenger` 表加 `user_id bigint` 列 + `idx_user_id` 索引
- [x] 1.2 `Passenger.java`: 加 `@TableField("user_id") private Long userId;` 字段
- [x] 1.3 编写存量数据回填 SQL（UPDATE t_passenger JOIN t_user SET user_id = u.id WHERE user_id IS NULL），放入 `db_table.sql` 注释中

## 2. Enums & Chain Interfaces

- [x] 2.1 `ChainMarkEnum.java`: 添加 `PASSENGER_CREATE`, `PASSENGER_UPDATE`, `PASSENGER_DELETE` 三个枚举值
- [x] 2.2 `PassengerCreateChainFilter.java`: 新建链标记接口，parameterized type = `PassengerCreateReqDTO`
- [x] 2.3 `PassengerUpdateChainFilter.java`: 新建链标记接口，parameterized type = `PassengerUpdateReqDTO`
- [x] 2.4 `PassengerDeleteChainFilter.java`: 新建链标记接口，parameterized type = `Long`（passengerId）

## 3. DTOs

- [x] 3.1 `PassengerCreateReqDTO.java`: realName, idType, idCard, phone, discountType (选填)
- [x] 3.2 `PassengerUpdateReqDTO.java`: phone (选填), discountType (选填)
- [x] 3.3 `PassengerRespDTO.java`: id, realName, idType, idCard (脱敏), phone (脱敏), discountType, verifyStatus, createDate
- [x] 3.4 在 DTO 或工具方法中实现脱敏逻辑：身份证保留首 3 尾 4 位，手机号保留首 3 尾 4 位（实现在 PassengerServiceImpl.toRespDTO）

## 4. Chain Handlers

- [x] 4.1 `PassengerCreateParamNotNullChainHandler.java`: order=0，校验 requestParam / realName / idType / idCard / phone 非空
- [x] 4.2 `PassengerCreateParamVerifyChainHandler.java`: order=5，校验 idType 枚举、discountType 枚举、手机号格式、同用户同证件号不重复、乘车人数量 ≤15
- [x] 4.3 `PassengerUpdateParamNotNullChainHandler.java`: order=0，校验 requestParam 非空
- [x] 4.4 `PassengerUpdateParamVerifyChainHandler.java`: order=5，校验手机号格式、discountType 枚举（归属校验在 Service 层）
- [x] 4.5 `PassengerDeleteParamVerifyChainHandler.java`: order=0，校验 passengerId 非空、乘车人存在、归属当前用户、无进行中订单

## 5. Service

- [x] 5.1 `PassengerService.java`: 接口定义 create / update / delete / listMine 四个方法
- [x] 5.2 `PassengerServiceImpl.java`: 实现类，extends ServiceImpl<PassengerMapper, Passenger>，每个方法 = 链校验 → 归属注入 → 业务逻辑 → 返回 DTO
- [x] 5.3 create 方法: userId/username 从 UserContext 注入，不接受前端传值，创建后返回 PassengerRespDTO
- [x] 5.4 update 方法: 仅更新 phone 和 discountType（非空才更新），证件字段不可改
- [x] 5.5 delete 方法: 软删除（delFlag=1），校验通过后执行 deleteById
- [x] 5.6 listMine 方法: 按 userId 查全部（最多 15），按 createTime 排序，返回脱敏 DTO

## 6. Controller

- [x] 6.1 `PassengerController.java`: @RestController + @RequestMapping("/passenger") + @RequiredArgsConstructor
- [x] 6.2 POST /passenger — create，返回 Result<PassengerRespDTO>
- [x] 6.3 PUT /passenger/{id} — update，返回 Result<PassengerRespDTO>
- [x] 6.4 DELETE /passenger/{id} — delete，返回 Result<Void>
- [x] 6.5 GET /passenger/list — listMine，返回 Result<List<PassengerRespDTO>>

## 7. Existing Code Migration

- [x] 7.1 `OrderRefundStatusChainHandler.java`: 将 `passengerMapper.selectList(..., eq(Passenger::getUsername, ...))` 替换为 `passengerService.listMine()` 按 userId 查询
- [x] 7.2 检查其他直接引用 `PassengerMapper` 的文件（OrderCreateParamVerifyChainHandler, SeatSelector, WaitlistServiceImpl），确认只读场景无需改动

## 8. Verification

- [x] 8.1 `./mvnw compile` 编译通过
- [ ] 8.2 启动应用，调用 POST/PUT/DELETE/GET list 四个接口验证
- [ ] 8.3 验证创建校验：空字段拒绝、超 15 人拒绝、证件号重复拒绝
- [ ] 8.4 验证删除校验：有未完成订单拒绝、非本人拒绝
- [ ] 8.5 验证脱敏：列表中 idCard 和 phone 正确脱敏
