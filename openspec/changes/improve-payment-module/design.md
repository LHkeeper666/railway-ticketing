## Context

当前支付逻辑全部耦合在 `OrderServiceImpl`（`payOrder`、`handlePayNotify`、`saveOrUpdatePay` 三个方法，约 160 行），无渠道抽象、无签名校验。Pay 状态使用硬编码字符串散落在 6+ 个文件中。`PayCallbackReqDTO.sign` 字段已定义但从未使用。`Pay.channel` 字段已预留但从未在支付创建时赋值。

项目已采用责任链模式做参数校验，`ORDER_PAY` 和 `PAY_NOTIFY` 两组链已存在。架构遵循 Controller → Service(接口+实现) → Mapper 分层。

## Goals / Non-Goals

**Goals:**
- 引入 `PaymentStrategy` 策略接口，将支付渠道差异封装到策略实现中
- 创建独立的 `PaymentService` 门面，从 `OrderServiceImpl` 剥离支付逻辑
- Mock 策略补齐签名生成与回调验签，演示完整支付链路
- 统一所有 Pay 状态引用为 `PayStatusEnum` 枚举
- 新增 `MockPayController` 模拟支付页面，验证完整回调流程

**Non-Goals:**
- 不接入真实支付宝/微信支付（仅预留扩展点）
- 不改动 t_pay 表结构（现有字段已足够）
- 不修改退款/候补支付的业务逻辑（仅替换枚举引用）
- 不修改前端代码（Mock 支付页面返回 JSON）

## Decisions

### Decision 1: 策略接口设计 —— 四个核心方法

```java
public interface PaymentStrategy {
    String getChannel();                          // "MOCK" / "ALIPAY" / "WECHAT"
    PayCreateResult createPayment(PayCreateRequest req);
    boolean verifySignature(PayCallbackReqDTO callback);
    PayQueryResult queryStatus(String orderSn);
    RefundResult refund(RefundRequest req);
}
```

**Why**: `getChannel()` 用于工厂路由，`createPayment()` 封装渠道特有的下单逻辑，`verifySignature()` 独立出来方便责任链调用，`queryStatus()` 用于主动查单（补单/轮询），`refund()` 渠道退款。

**Alternatives considered**: Visitor 模式或模板方法 → 策略模式更轻量，Spring 的 `List<PaymentStrategy>` 注入自动收集所有实现。

### Decision 2: PaymentServiceImpl 门面职责划分

```
PaymentServiceImpl:
  ├── 按 channel 路由 PaymentStrategy
  ├── 通用逻辑：写 Pay 表、CAS 更新 Order 状态、封装 DTO
  └── 不包含渠道特有逻辑（签名、通信、报文格式）

PaymentStrategy 实现:
  ├── 渠道特有逻辑：生成支付链接、签名/验签、报文组装
  └── 不直接操作数据库（通过注入 Mapper 或由 PaymentServiceImpl 传入）
```

**Why**: 门面处理通用生命周期（PENDING→SUCCESS/FAIL），策略处理渠道差异。新增渠道只需新建一个策略实现类，无需改动门面。

### Decision 3: Mock 签名方案 —— HMAC-SHA256

签名规则: `HMAC-SHA256(paySn + "|" + orderSn + "|" + totalAmount + "|" + status, secretKey)`

`secretKey` 配置在 `application.yaml` 的 `payment.mock.secret`。生成签名时存入 Pay 表的 `trade_no` 字段（复用已有字段），回调时从 Pay 表读取重新计算比对。

**Why**: 零依赖，面试时可说明"支付宝用的是 RSA2 非对称加密验签，我这里更简单用的是 HMAC 对称签名，原理类似"。**Alternatives**: 引入 RSA 密钥对 → 对 Mock 场景过度设计。

### Decision 4: 责任链签名校验位置

```
PayNotifyChainFilter (mark=PAY_NOTIFY):
  order=0: PayNotifyParamNotNullChainHandler        ← 已有
  order=3: PayNotifySignVerifyChainHandler           ← 新增
  order=5: PayNotifyParamValidateChainHandler        ← 已有
```

签名校验放在参数非空之后、业务校验之前——签名不通过直接拒绝，不需要再查数据库。

### Decision 5: PayStatusEnum 枚举值

```java
public enum PayStatusEnum {
    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    FAIL("FAIL"),
    FROZEN("FROZEN"),
    PENDING_REFUND("PENDING_REFUND"),
    REFUNDED("REFUNDED");
}
```

**Why**: 保持与现有字符串值一致，替换时不需要改数据库存储格式。不影响已有数据。

### Decision 6: OrderServiceImpl 改造范围

删除 `payOrder()` (L416-451)、`handlePayNotify()` (L459-529)、`saveOrUpdatePay()` (L531-577) 三个方法。新增注入 `PaymentService`，`payOrder` 和 `handlePayNotify` 的 Controller 调用直接委托给 `PaymentService`。`cancelOrder()` 中的 Pay 状态更新改为使用 `PayStatusEnum.REFUNDED`。

## Risks / Trade-offs

- **风险**: `handlePayNotify` 中签名校验依赖 Pay 表数据 → 如果 Pay 记录不存在（伪造订单号），需要先判空。**缓解**: 签名校验 Handler 中查不到 Pay 记录直接抛 `ClientException("非法回调")`。
- **风险**: `MockPayController` 暴露在公网可能被滥用 → **缓解**: Interceptor 注册时排除 `/mock-pay/**` 路径，或使用 `@Profile("dev")` 限制。
- **取舍**: 策略的 `createPayment()` 直接返回 `PayCreateResult`，但不同渠道返回内容差异大（支付宝是 HTML 表单，微信是 prepay_id）。**选择**: `PayCreateResult` 使用 Map 类型的 `extra` 字段承载渠道特有数据。
