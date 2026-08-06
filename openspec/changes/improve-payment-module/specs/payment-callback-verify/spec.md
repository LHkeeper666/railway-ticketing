## ADDED Requirements

### Requirement: Signature verification in callback chain
The system SHALL add a `PayNotifySignVerifyChainHandler` to the `PAY_NOTIFY` responsibility chain at order=3 (after parameter not-null check, before business validation). This handler SHALL invoke `PaymentStrategy.verifySignature()` for the corresponding channel and reject the callback if signature verification fails.

#### Scenario: Callback with valid signature passes chain
- **WHEN** payment callback DTO passes parameter not-null check
- **THEN** `PayNotifySignVerifyChainHandler` verifies the signature via the channel's strategy, and the chain proceeds to business validation

#### Scenario: Callback with invalid signature rejected
- **WHEN** payment callback DTO has an invalid or missing signature
- **THEN** `PayNotifySignVerifyChainHandler` throws `ClientException("签名校验失败")` and the callback is rejected before any business logic executes

#### Scenario: Callback with unknown channel rejected
- **WHEN** payment callback DTO has a `channel` value that does not match any registered `PaymentStrategy`
- **THEN** `PayNotifySignVerifyChainHandler` throws `ClientException("不支持的支付渠道")`

### Requirement: Chain order for PAY_NOTIFY
The `PAY_NOTIFY` responsibility chain SHALL execute handlers in this order: parameter not-null (order=0), signature verification (order=3), business validation (order=5).

#### Scenario: Chain execution order
- **WHEN** a `POST /order/pay/notify` request arrives
- **THEN** handlers execute in order: `PayNotifyParamNotNullChainHandler` → `PayNotifySignVerifyChainHandler` → `PayNotifyParamValidateChainHandler`
