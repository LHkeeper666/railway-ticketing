## Context

当前系统已有 Auth（登录注册）、Order（下单/退票/改签）、Ticket（余票查询）、Passenger（乘车人 CRUD）模块。用户中心和订单列表是核心业务闭环的最后两块拼图——完成后 C 端用户从注册到登录到购票到查看历史订单的完整链路就打通了。

项目现有技术约束：
- 责任链模式统一处理参数校验（`AbstractChainFilter` + `AbstractChainContext`）
- 构造注入 + Lombok `@RequiredArgsConstructor`
- MyBatis-Plus LambdaQueryWrapper 风格，Mapper/XML 均为空
- 统一响应 `Result<T>`，异常分 `ClientException`（400）和 `ServiceException`（500）
- JWT 无状态认证，ThreadLocal 持有 `UserContext`

## Goals / Non-Goals

**Goals:**
- 4 个用户中心接口：查看/修改个人信息、修改密码、注销账号
- 1 个订单列表接口：分页 + 状态/日期/车次号筛选
- 身份证号 AES 加密存储 + 响应脱敏
- 通用分页响应 `PageResponse<T>` 可复用
- 为微服务化预留接口抽象（UserService 不传 userId 参数，Order 表数据自包含）

**Non-Goals:**
- 不修改现有接口签名
- 不涉及管理后台（所有接口均为当前用户操作自己的数据）
- 不新增第三方依赖（AES 用 JDK 自带 `javax.crypto`）
- 不做 JWT 黑名单（注销后 token 自然过期）

## Decisions

### 决策 1：UserService 不暴露 userId 参数

UserService 所有方法通过 `UserContext.get().getUserId()` 获取当前用户，不接受外部传入的 userId。

**理由**：微服务化后 UserService 拆分出去，RPC 层从 token 解析 userId 传入，接口签名不变。禁止一个用户查另一个用户的信息——如有管理需求后续单独做 `AdminUserService`。

**Alternatives considered**: 传 userId 参数 → 更灵活但接口边界模糊，微服务化时上游可以直接传任意 userId 绕过权限，不如从设计层面杜绝。

### 决策 2：身份证号 AES 加密存储

使用 JDK 自带 `javax.crypto.Cipher`（AES/CBC/PKCS5Padding），密钥从 `application.yaml` 的 `aes.secret-key` 注入。

`AesUtil` 工具类提供 `encrypt(String plain)` / `decrypt(String cipher)` / `mask(String idCard)` 三个方法。加密时机在 Service 层写 DB 前，解密在 Service 层读 DB 后。脱敏在构造 DTO 时调用 `mask()`，DB 中永远只有密文。

**Alternatives considered**:
- 不加密存明文 → 安全风险，2021 年《个人信息保护法》要求身份证号加密存储
- 用 Spring Security Crypto `TextEncryptor` → 多一个依赖，JDK 自带的够用

### 决策 3：订单列表用 LambdaQueryWrapper 而非 XML Mapper

```java
LambdaQueryWrapper<Order> wrapper = Wrappers.<Order>lambdaQuery()
    .eq(Order::getUserId, userId)
    .eq(status != null, Order::getStatus, status)
    .ge(startDate != null, Order::getRidingDate, startDate)
    .le(endDate != null, Order::getRidingDate, endDate)
    .like(StrUtil.isNotBlank(trainNumber), Order::getTrainNumber, trainNumber)
    .orderByDesc(Order::getOrderTime);
orderMapper.selectPage(new Page<>(current, size), wrapper);
```

**理由**：项目现有风格一致（所有查询都用 LambdaQueryWrapper，Mapper 无自定义方法）。这种简单条件拼接不需要手写 SQL。面试时可以说"LambdaQueryWrapper 保证类型安全，避免 SQL 字符串拼接错误"。

**Alternatives considered**: XML Mapper → 更灵活但破坏一致性，且当前查询足够简单不需要手写 SQL。

### 决策 4：订单列表返回简洁 DTO，不含 OrderItem 详情

`OrderListRespDTO` 只包含订单头信息 + totalAmount（汇总）+ passengerCount。点进详情用已有的 `GET /order/{orderSn}` 接口。

**理由**：避免 N+1 查询。列表接口一次查 10 条订单，如果每条都 JOIN 查 OrderItem/Ticket，查询复杂度 O(n*m)。汇总金额和人数可以在查询时子查询或 Service 层聚合，也可以用冗余字段（如果后续数据量大建议给 Order 表加 total_amount 和 passenger_count 冗余字段）。

当前实现方案：订单头查出来后，批量查对应 OrderItem，在 Java 内存中计算 totalAmount 和 passengerCount。数据量小（每用户几百笔订单）够用，面试时可以提"数据量大后可冗余字段或用 MyBatis 子查询优化"。

### 决策 5：注销为软删除，不处理 JWT 黑名单

注销调用 `userMapper.updateById` 设 `delFlag=1` 和 `deletionTime`，利用 `@TableLogic` 自动过滤。JWT 在有效期内仍可用。

**理由**：JWT 无状态是设计特性而非缺陷。Redis 黑名单方案引入了状态，与 JWT 设计初衷矛盾。面试时可以主动说明权衡："我知道注销后 JWT 不会立即失效，生产环境可以缩短 token 有效期（比如 15min）+ refresh token 机制来缓解，或者用 Redis 存黑名单但会引入状态。"

**Alternatives considered**: Redis 黑名单 → 面试加分但项目复杂度提升，当前作为面试项目保持简单更合理。

### 决策 6：PageResponse 放 common/page/ 而非 common/result/

新建 `PageResponse<T>`（`total`, `current`, `size`, `records`），与 `PageRequest` 放在同一包下。后续 Ticket 列表查询也可以复用。

提供静态工厂方法 `PageResponse.from(IPage<T> page)` 方便从 MyBatis-Plus 分页结果转换。

## Risks / Trade-offs

- **[身份证解密失败]** → 原因可能是密钥变更或数据损坏。首次上线时加密逻辑仅对新数据生效，旧数据可能为明文。**Mitigation**: `decrypt()` 方法 catch 异常后返回原文（兼容明文存量数据），上线后逐步迁移。
- **[订单列表性能]** → 用户订单量大时（>1000 笔），LambdaQuery 全表扫描不可接受。**Mitigation**: 建议加 `(user_id, status, order_time)` 联合索引；数据量>10 万时建议归档历史订单到 `t_order_archive`。
- **[注销后 Token 仍可用]** → 已注销用户在 token 有效期内仍可调用需认证的接口。**Mitigation**: 文档明确说明，面试时主动解释权衡。

## Open Questions

1. **身份证号存量数据处理**：现有可能已有明文 idCard 数据，`decrypt()` 中 catch 异常返回原文的方案是否合适？
2. **修改密码后是否需要让旧 JWT 失效**：如果用户改了密码，旧的 JWT 是否应该失效？技术上需要 Redis 黑名单，复杂度增加。
