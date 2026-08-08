## ADDED Requirements

### Requirement: Admin role field in user table
The system SHALL store a `role` field in `t_user` table (TINYINT, DEFAULT 0) where 0 represents regular user and 1 represents admin.

#### Scenario: New user registration defaults to regular user
- **WHEN** a new user registers via `POST /auth/register`
- **THEN** the user's role SHALL be set to 0 (user)

#### Scenario: Admin role is included in JWT token
- **WHEN** a user logs in successfully via `POST /auth/login`
- **THEN** the JWT token SHALL include a `role` claim with the user's role value

### Requirement: AdminRequired annotation and interceptor
The system SHALL provide an `@AdminRequired` annotation and an `AdminInterceptor` that intercepts all `/admin/**` requests and verifies the authenticated user has role=1.

#### Scenario: Admin accesses admin endpoint
- **WHEN** a user with role=1 sends a request to any `/admin/**` endpoint with a valid JWT
- **THEN** the request SHALL proceed to the controller

#### Scenario: Regular user accesses admin endpoint
- **WHEN** a user with role=0 sends a request to any `/admin/**` endpoint with a valid JWT
- **THEN** the system SHALL return HTTP 403 with message "无管理员权限"

#### Scenario: Unauthenticated user accesses admin endpoint
- **WHEN** a request without a valid JWT is sent to any `/admin/**` endpoint
- **THEN** the system SHALL return HTTP 401 with message "未登录"

### Requirement: UserInfo carries role field
The `UserInfo` DTO held in `UserContext` ThreadLocal SHALL include a `role` field, populated by `JwtInterceptor` from the JWT `role` claim.

#### Scenario: UserContext contains role after authentication
- **WHEN** `JwtInterceptor` successfully parses a JWT token
- **THEN** `UserContext.get().getRole()` SHALL return the user's role value
