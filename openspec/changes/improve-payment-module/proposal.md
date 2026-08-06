## Why

当前支付模块完全耦合在 `OrderServiceImpl` 中，采用纯模拟方式（仅创建 Pay 记录，不返回支付链接/二维码），缺少渠道抽象、签名校验和独立的支付服务层。面试场景下无法体现支付系统的设计深度，改造后可展示策略模式、回调验签、状态机等核心设计能力。

## What Changes

- **新增** `PayStatusEnum` 枚举，统一当前散落在代码各处的硬编码支付状态字符串（PENDING/SUCCESS/FAIL/FROZEN/PENDING_REFUND/REFUNDED）
- **新增** `PaymentStrategy` 接口（策略模式），定义 `createPayment`、`verifySignature`、`queryStatus`、`refund` 四个渠道抽象方法
- **新增** `PaymentService` 接口 + `PaymentServiceImpl`，从 `OrderServiceImpl` 剥离支付逻辑，作为支付门面按 channel 路由策略
- **新增** `MockPaymentStrategy`，将现有模拟支付逻辑移入，并补齐：支付链接生成、HMAC-SHA256 签名、回调验签
- **新增** `MockPayController`，提供模拟支付页面（GET 查看订单 + POST 确认支付），确认支付走完整回调链路
- **新增** `PayNotifySignVerifyChainHandler` 责任链处理器，回调时先验签再处理业务
- **修改** `OrderServiceImpl`，删除 `payOrder()`、`handlePayNotify()`、`saveOrUpdatePay()` 三个方法，改为注入 `PaymentService`
- **修改** `OrderController`，pay 接口返回类型改为 `PayCreateResult`（含支付链接）
- **修改** `WaitlistServiceImpl`、`RefundServiceImpl`、`ChangeServiceImpl`、`RefundPendingTask`、`WaitlistExpireTask` 中的 Pay 状态字符串替换为 `PayStatusEnum`

## Capabilities

### New Capabilities
- `payment-strategy`: 支付策略模式，定义统一的支付渠道抽象接口（PaymentStrategy），支持 Mock 实现，预留支付宝/微信扩展点
- `payment-service`: 独立支付服务层，将支付创建、回调处理、退款从 OrderService 剥离为 PaymentService 门面
- `payment-callback-verify`: 支付回调签名校验，通过责任链统一验签，防伪造回调

### Modified Capabilities
<!-- No existing spec-level requirements are changing -->

## Impact

- **OrderServiceImpl**: 删除 ~150 行支付相关代码，改为调用 PaymentService
- **OrderController**: `/order/{sn}/pay` 返回类型调整
- **OrderService 接口**: 删除 `payOrder()`、`handlePayNotify()` 方法签名
- **Pay 实体 / t_pay 表**: 无需改动（已有 channel、sign 等预留字段）
- **责任链 ChainMarkEnum**: 可能需要新增 `PAY_NOTIFY_SIGN_VERIFY` 或复用 `PAY_NOTIFY` 组内排序
- **候补/退票/改签**: 仅将硬编码状态字符串替换为 PayStatusEnum，逻辑不变
