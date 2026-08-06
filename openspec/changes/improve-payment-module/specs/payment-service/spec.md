## ADDED Requirements

### Requirement: Payment service facade
The system SHALL provide a `PaymentService` interface and `PaymentServiceImpl` implementation that acts as a facade for all payment operations, decoupling payment logic from `OrderServiceImpl`. The facade SHALL be a Spring `@Service` Bean.

#### Scenario: Create payment via facade
- **WHEN** `PaymentService.createPayment(reqDTO)` is called with a valid order
- **THEN** the facade routes to the appropriate `PaymentStrategy` by channel, creates a Pay record with status PENDING, and returns a `PayCreateResult` containing payment URL and signature

#### Scenario: Handle payment callback via facade
- **WHEN** `PaymentService.handleCallback(reqDTO)` is called
- **THEN** the facade verifies the signature via the corresponding strategy, then updates Pay and Order statuses using CAS (UPDATE WHERE status=? pattern)

#### Scenario: Query payment status
- **WHEN** `PaymentService.queryPayment(orderSn)` is called
- **THEN** the system returns a `PayInfoDTO` containing paySn, channel, tradeNo, totalAmount, status, and payment time

### Requirement: OrderServiceImpl delegates payment to PaymentService
`OrderServiceImpl` SHALL delegate payment creation and callback handling to `PaymentService` instead of implementing these operations directly. The methods `payOrder()`, `handlePayNotify()`, and `saveOrUpdatePay()` SHALL be removed from `OrderServiceImpl`.

#### Scenario: payOrder delegates to PaymentService
- **WHEN** `POST /order/{orderSn}/pay` is called
- **THEN** `OrderController` calls `OrderService.payOrder()` which delegates to `PaymentService.createPayment()`, and the response type is updated from `Result<Void>` to `Result<PayCreateResult>`

#### Scenario: handlePayNotify delegates to PaymentService
- **WHEN** `POST /order/pay/notify` is called
- **THEN** `OrderController` calls `PaymentService.handleCallback()` directly

### Requirement: PayStatusEnum replaces hardcoded strings
The system SHALL provide a `PayStatusEnum` enumeration with values `PENDING`, `SUCCESS`, `FAIL`, `FROZEN`, `PENDING_REFUND`, `REFUNDED`. All code referencing Pay status as raw strings SHALL be updated to use this enum.

#### Scenario: All Pay status references use enum
- **WHEN** any code creates or updates a Pay record's status
- **THEN** the status value is set via `PayStatusEnum.XXX.name()` or a direct enum reference, not a raw string literal

#### Scenario: Enum values match existing database values
- **WHEN** `PayStatusEnum.PENDING.name()` is called
- **THEN** it returns `"PENDING"`, matching the existing database storage format
