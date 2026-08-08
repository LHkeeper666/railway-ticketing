## ADDED Requirements

### Requirement: Admin can query all users
The system SHALL provide `GET /admin/user/page` for admins to query users with filters.

#### Scenario: Query users by keyword
- **WHEN** an admin sends `GET /admin/user/page?current=1&size=20&keyword=张三`
- **THEN** the system SHALL return a paginated list of users matching the keyword in username, real_name, or phone

#### Scenario: Query users by role
- **WHEN** an admin sends `GET /admin/user/page?current=1&size=20&role=1`
- **THEN** the system SHALL return only admin users

### Requirement: Admin can view user detail
The system SHALL provide `GET /admin/user/{id}` for admins to view any user's information.

#### Scenario: View user detail
- **WHEN** an admin sends `GET /admin/user/{id}`
- **THEN** the system SHALL return the user's profile information including id_card (masked as first 3 + last 4 characters), phone, email, and registration time

### Requirement: Admin can enable or disable users
The system SHALL provide `PUT /admin/user/{id}/status` for admins to enable or disable user accounts.

#### Scenario: Disable a user
- **WHEN** an admin sends `PUT /admin/user/{id}/status` with `{ "status": 0 }` (disabled)
- **THEN** the system SHALL update the user's status. Disabled users SHALL NOT be able to log in.

#### Scenario: Enable a previously disabled user
- **WHEN** an admin sends `PUT /admin/user/{id}/status` with `{ "status": 1 }` (enabled)
- **THEN** the system SHALL update the user's status, restoring their ability to log in

#### Scenario: Disabled user login attempt
- **WHEN** a disabled user attempts to log in via `POST /auth/login`
- **THEN** the system SHALL return error "账号已被禁用"

### Requirement: Admin cannot modify user password
The system SHALL NOT allow admins to view or modify user passwords.

#### Scenario: Admin views user detail without password
- **WHEN** an admin requests any user detail
- **THEN** the password field SHALL NOT be included in the response
