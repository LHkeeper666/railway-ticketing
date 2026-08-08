## ADDED Requirements

### Requirement: Admin can query all orders
The system SHALL provide `GET /admin/order/page` for admins to query orders across all users with filters.

#### Scenario: Query orders by status
- **WHEN** an admin sends `GET /admin/order/page?current=1&size=20&status=UNPAID`
- **THEN** the system SHALL return a paginated list of all UNPAID orders from all users

#### Scenario: Query orders by train number
- **WHEN** an admin sends `GET /admin/order/page?current=1&size=20&trainNumber=G35`
- **THEN** the system SHALL return orders for train G35 across all users

#### Scenario: Query orders by user ID
- **WHEN** an admin sends `GET /admin/order/page?current=1&size=20&userId=123`
- **THEN** the system SHALL return orders belonging to user 123

#### Scenario: Query orders by date range
- **WHEN** an admin sends `GET /admin/order/page?current=1&size=20&startDate=2026-01-01&endDate=2026-01-31`
- **THEN** the system SHALL return orders within the date range

### Requirement: Admin can view order detail
The system SHALL provide `GET /admin/order/{orderSn}` to view full order details including all tickets and payment records.

#### Scenario: View any order detail
- **WHEN** an admin sends `GET /admin/order/{orderSn}` for any order in the system
- **THEN** the system SHALL return the order with all associated order items, tickets, and payment records, regardless of which user owns the order

### Requirement: Admin can manually cancel or refund orders
The system SHALL allow admins to manually cancel or refund orders via `POST /admin/order/{orderSn}/cancel` and `POST /admin/order/{orderSn}/refund`.

#### Scenario: Admin cancels an unpaid order
- **WHEN** an admin sends `POST /admin/order/{orderSn}/cancel`
- **THEN** the system SHALL execute the cancelOrder flow: release seat bitmap, update order/item/ticket status to CANCELED/CLOSED, clear cache, and trigger waitlist matching

#### Scenario: Admin refunds a paid order
- **WHEN** an admin sends `POST /admin/order/{orderSn}/refund` with an optional `reason` field
- **THEN** the system SHALL execute the refund flow including refund fee calculation and seat release

#### Scenario: Admin cancels an already-canceled order
- **WHEN** an admin sends `POST /admin/order/{orderSn}/cancel` for an already CANCELED order
- **THEN** the system SHALL return success with message "订单已取消" (idempotent)
