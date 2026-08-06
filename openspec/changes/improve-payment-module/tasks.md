## 1. 基础枚举与 DTO

- [x] 1.1 新建 `PayStatusEnum` 枚举，包含 PENDING/SUCCESS/FAIL/FROZEN/PENDING_REFUND/REFUNDED 六个值
- [x] 1.2 新建 `dto/PayCreateRequest.java`，包含 orderSn、channel、totalAmount、subject 字段
- [x] 1.3 新建 `dto/resp/PayCreateResult.java`，包含 paySn、orderSn、totalAmount、payUrl、sign、extra(Map) 字段
- [x] 1.4 新建 `dto/resp/MockPayPageDTO.java`，包含 paySn、orderSn、totalAmount、status、subject 字段

## 2. 支付策略模式核心

- [x] 2.1 新建 `service/PaymentStrategy.java` 接口，定义 getChannel、createPayment、verifySignature、queryStatus、refund 五个方法
- [x] 2.2 新建 `service/PaymentService.java` 接口，定义 createPayment、handleCallback、queryPayment、refund 四个方法
- [x] 2.3 新建 `service/impl/PaymentServiceImpl.java`，实现渠道路由、Pay 表操作、CAS 状态更新（迁移自 OrderServiceImpl 的 handlePayNotify 逻辑）

## 3. Mock 支付实现

- [x] 3.1 新建 `service/impl/payment/MockPaymentStrategy.java`，实现 PaymentStrategy 接口：createPayment 生成 HMAC-SHA256 签名、写 Pay(PENDING)、返回模拟支付 URL；verifySignature 重新计算比对签名
- [x] 3.2 新建 `controller/MockPayController.java`，实现 GET /mock-pay/{paySn}（展示订单信息）和 POST /mock-pay/{paySn}/pay（确认支付，走完整回调流程）
- [x] 3.3 在 `application.yaml` 中添加 `payment.mock.secret` 配置项

## 4. 回调签名校验责任链

- [x] 4.1 新建 `service/handler/filter/PayNotifySignVerifyChainHandler.java`，实现 `PayNotifyChainFilter`，order=3，校验回调签名
- [x] 4.2 确认 PAY_NOTIFY 链执行顺序：order=0 参数非空 → order=3 签名校验（业务校验已内联在 PaymentServiceImpl.handleCallback 中）

## 5. 重构 OrderService 支付逻辑

- [x] 5.1 修改 `OrderServiceImpl`：注入 `PaymentService`，删除 `payOrder()`、`handlePayNotify()`、`saveOrUpdatePay()` 方法，payOrder 和 handlePayNotify 调用委托给 PaymentService；cancelOrder 中 Pay 状态改为 PayStatusEnum
- [x] 5.2 修改 `OrderService` 接口：更新 `payOrder()` 返回类型为 `PayCreateResult`，或将方法委托说明更新
- [x] 5.3 修改 `OrderController`：pay 接口返回类型改为 `Result<PayCreateResult>`

## 6. 全局 PayStatusEnum 替换

- [x] 6.1 修改 `WaitlistServiceImpl`：Pay 状态字符串 → PayStatusEnum
- [x] 6.2 修改 `RefundServiceImpl`：Pay 状态字符串 → PayStatusEnum
- [x] 6.3 修改 `ChangeServiceImpl`：Pay 状态字符串 → PayStatusEnum
- [x] 6.4 修改 `RefundPendingTask`：Pay 状态字符串 → PayStatusEnum
- [x] 6.5 修改 `WaitlistExpireTask`：Pay 状态字符串 → PayStatusEnum
- [x] 6.6 修改 `Pay.java` 实体：status 字段注释标注使用 PayStatusEnum

## 7. 验证

- [x] 7.1 运行 `mvn compile` 确保编译通过
- [x] 7.2 验证 MockPayController 路径在 JwtInterceptor 排除列表中（已添加 `/mock-pay/**`）
