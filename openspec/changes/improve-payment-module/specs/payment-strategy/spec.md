## ADDED Requirements

### Requirement: Payment channel abstraction
The system SHALL provide a `PaymentStrategy` interface that abstracts payment channel operations, including `createPayment`, `verifySignature`, `queryStatus`, and `refund`. Each payment channel (Mock, Alipay, Wechat) SHALL implement this interface as a Spring Bean.

#### Scenario: Mock payment strategy registered
- **WHEN** application starts
- **THEN** `MockPaymentStrategy` is automatically registered as a Spring Bean and discoverable via `List<PaymentStrategy>`

#### Scenario: Channel routing by channel identifier
- **WHEN** `PaymentService` receives a payment request with `channel="MOCK"`
- **THEN** the system routes to `MockPaymentStrategy` based on `getChannel()` return value

#### Scenario: New channel added without modifying facade
- **WHEN** a new `AlipaySandboxStrategy` implements `PaymentStrategy` and is registered as a Bean
- **THEN** `PaymentServiceImpl` automatically discovers it without any code change to the facade

### Requirement: Mock payment strategy generates payment URL
The `MockPaymentStrategy` SHALL generate a payment URL (`/mock-pay/{paySn}`) when creating a payment, and SHALL generate an HMAC-SHA256 signature stored in the Pay record for callback verification.

#### Scenario: Mock payment creation returns payment URL
- **WHEN** user initiates payment via Mock channel
- **THEN** the response includes a `payUrl` field containing `/mock-pay/{paySn}`

#### Scenario: Mock signature generated and stored
- **WHEN** Mock payment is created
- **THEN** an HMAC-SHA256 signature is computed from `paySn|orderSn|totalAmount|status` and stored in the Pay record's `trade_no` field

### Requirement: Mock payment page
The system SHALL provide a `MockPayController` with two endpoints: `GET /mock-pay/{paySn}` to display order payment information, and `POST /mock-pay/{paySn}/pay` to confirm payment.

#### Scenario: View mock payment page
- **WHEN** user visits `GET /mock-pay/{paySn}`
- **THEN** the response includes order amount, order SN, and payment status

#### Scenario: Confirm mock payment
- **WHEN** user posts to `POST /mock-pay/{paySn}/pay` to confirm payment
- **THEN** the system invokes `PaymentService.handleCallback()` with a `PayCallbackReqDTO` containing the correct signature, and the order transitions to PAID

#### Scenario: Mock payment with invalid signature rejected
- **WHEN** a callback is posted with an incorrect or missing signature
- **THEN** the system rejects it with `ClientException("签名校验失败")`

### Requirement: Payment strategy signature verification
Each `PaymentStrategy` SHALL implement `verifySignature(PayCallbackReqDTO)` to validate callback authenticity. `MockPaymentStrategy` SHALL use HMAC-SHA256. Future channel implementations (Alipay, Wechat) SHALL use their respective signature algorithms.

#### Scenario: Mock signature verification passes
- **WHEN** callback DTO contains a `sign` field matching the recomputed HMAC-SHA256
- **THEN** `verifySignature()` returns true

#### Scenario: Mock signature verification fails
- **WHEN** callback DTO contains a tampered `sign` field or the payload is modified
- **THEN** `verifySignature()` returns false
