## ADDED Requirements

### Requirement: Admin can manage trains
The system SHALL provide CRUD endpoints under `/admin/train` for managing trains.

#### Scenario: List trains with pagination and filters
- **WHEN** an admin sends `GET /admin/train/page?current=1&size=20&trainNumber=G35&trainType=0`
- **THEN** the system SHALL return a paginated list of trains matching the filters

#### Scenario: Get train detail with full configuration
- **WHEN** an admin sends `GET /admin/train/{id}`
- **THEN** the system SHALL return train info plus all associated stations, carriages, seats, and prices

#### Scenario: Create a new train with basic info
- **WHEN** an admin sends `POST /admin/train` with train_number, train_type, train_tag, train_brand, start_station, end_station, departure_time, arrival_time, sale_time
- **THEN** the system SHALL insert the train into `t_train` with `sale_status=0` and return the created train

#### Scenario: Update train metadata with no active orders
- **WHEN** an admin sends `PUT /admin/train/{id}` updating metadata (times, names, tags) and the train has no UNPAID/PAID/TICKETED orders
- **THEN** the system SHALL update the train and clear relevant Redis caches

#### Scenario: Update train metadata with active orders
- **WHEN** an admin sends `PUT /admin/train/{id}` updating departure_time and the train has active orders
- **THEN** the system SHALL reject with message "该车次有未完成订单，时间变更请使用晚点接口 POST /admin/train/{id}/delay"

#### Scenario: Delete a train with no orders
- **WHEN** an admin sends `DELETE /admin/train/{id}` and the train has zero orders
- **THEN** the system SHALL physically delete the train and all associated carriages, seats, TrainStation, TrainStationRelation, TrainStationPrice records

#### Scenario: Delete a train with orders
- **WHEN** an admin sends `DELETE /admin/train/{id}` and the train has any order records
- **THEN** the system SHALL soft-delete the train (set `del_flag=1`) and return message "该车次有历史订单，已软删除"

### Requirement: Admin can configure train route (station list)
The system SHALL allow admins to set or modify the ordered station list of a train via `PUT /admin/train/{id}/stations`.

#### Scenario: Set route for a train with no orders (any structure)
- **WHEN** an admin sends `PUT /admin/train/{id}/stations` with a new station list and the train has zero orders
- **THEN** the system SHALL delete old TrainStation/Relation/Price records, insert new ones in the given order, reset all seat_bitmap to 0 and seat_status to AVAILABLE, regenerate Relations, and clear Redis cache

#### Scenario: Append station at end of route with active orders
- **WHEN** an admin sends `POST /admin/train/{id}/stations/append` to add a station at the end of the route and the train has active orders
- **THEN** the system SHALL insert the new TrainStation record at the end, regenerate Relations, and clear Redis cache. Existing seat_bitmap and purchaseMask values SHALL remain valid.

#### Scenario: Insert station in middle of route with active orders
- **WHEN** an admin sends `POST /admin/train/{id}/stations/insert` to insert a station between two existing stations and the train has active orders
- **THEN** the system SHALL reject with message "该车次有未完成订单，中间插入停站会导致座位位图错乱。请使用克隆功能 POST /admin/train/{id}/clone"

#### Scenario: Delete station from middle of route with active orders
- **WHEN** an admin sends `DELETE /admin/train/{id}/stations/{stationId}` for a middle station and the train has active orders
- **THEN** the system SHALL reject with message "该车次有未完成订单，删除中间停站会导致座位位图错乱。请使用克隆功能"

### Requirement: Admin can clone a train
The system SHALL provide `POST /admin/train/{id}/clone` to create a new train based on an existing one, with optional route modifications.

#### Scenario: Clone train with modified route
- **WHEN** an admin sends `POST /admin/train/{id}/clone` with a new train_number and a modified stations array
- **THEN** the system SHALL create a new Train record with the specified train_number, create new TrainStation records per the requested route, copy carriages and seats (resetting seat_bitmap to 0 and seat_status to AVAILABLE), auto-generate Relations and Prices, set the new train sale_status to 0, and freeze the old train (sale_status=1). All operations SHALL be in a single transaction.

#### Scenario: Clone train without route changes
- **WHEN** an admin sends `POST /admin/train/{id}/clone` with only a new train_number (no stations array)
- **THEN** the system SHALL clone the train using the original route

### Requirement: Admin can manage carriages and seats
The system SHALL provide endpoints for managing carriages and seats under `/admin/train/{id}/carriage*` and `/admin/train/{id}/seat*`.

#### Scenario: List carriages for a train
- **WHEN** an admin sends `GET /admin/train/{id}/carriages`
- **THEN** the system SHALL return all carriage records for the train

#### Scenario: Add a carriage with batch seat generation
- **WHEN** an admin sends `POST /admin/train/{id}/carriage` with carriage_number, carriage_type, seat_count
- **THEN** the system SHALL insert the carriage and automatically generate `seat_count` seat records with `seat_bitmap=0` and `seat_status=AVAILABLE`

#### Scenario: Delete a carriage
- **WHEN** an admin sends `DELETE /admin/train/{id}/carriage/{carriageId}`
- **THEN** the system SHALL delete the carriage and all associated seat records

### Requirement: Admin can manage station prices
The system SHALL provide endpoints for managing per-segment prices under `/admin/train/{id}/prices`.

#### Scenario: List all prices for a train
- **WHEN** an admin sends `GET /admin/train/{id}/prices`
- **THEN** the system SHALL return all `t_train_station_price` records for the train

#### Scenario: Batch update prices
- **WHEN** an admin sends `PUT /admin/train/{id}/prices/batch` with an array of (start_station, end_station, seat_type, price)
- **THEN** the system SHALL upsert the price records. Prices for the same (train_id, start_station, end_station, seat_type) SHALL be unique.

### Requirement: TrainStationRelation is auto-generated
The system SHALL automatically regenerate `t_train_station_relation` records whenever the train's station list changes. The table SHALL NOT require manual SQL population.

#### Scenario: Relations generated for a 4-station route
- **WHEN** a train has stations [A, B, C, D] in order
- **THEN** the system SHALL generate C(4,2)=6 relations: A→B, A→C, A→D, B→C, B→D, C→D, each with correct departure_flag, arrival_flag, departure_time, and arrival_time

#### Scenario: Relations regenerated after route change
- **WHEN** a train's station list is updated
- **THEN** the system SHALL DELETE all old relations for that train and INSERT new ones based on the new station list

### Requirement: Admin can announce train delay
The system SHALL provide `POST /admin/train/{id}/delay` for operational delays, semantically distinct from schedule adjustments.

#### Scenario: Announce a delay
- **WHEN** an admin sends `POST /admin/train/{id}/delay` with `{ "delayMinutes": 30 }`
- **THEN** the system SHALL update the train's departure_time and arrival_time, update all affected TrainStation departure/arrival times, update all affected TrainStationRelation times, and clear Redis cache

#### Scenario: Announce an early arrival (negative delay)
- **WHEN** an admin sends `POST /admin/train/{id}/delay` with `{ "delayMinutes": -10 }`
- **THEN** the system SHALL adjust times backward by 10 minutes for all affected records

### Requirement: Train route change safety checker
The system SHALL validate route modifications against active orders before allowing structural changes.

#### Scenario: Check allows append-only change
- **WHEN** `TrainStationChangeChecker.check(oldStations, newStations, hasActiveOrders=true)` is called with newStations being oldStations plus one appended station
- **THEN** the checker SHALL return `ALLOWED` with change type `APPEND`

#### Scenario: Check blocks middle insertion with active orders
- **WHEN** `TrainStationChangeChecker.check()` is called with a station inserted in the middle and hasActiveOrders is true
- **THEN** the checker SHALL return `BLOCKED` with reason indicating bitmap corruption risk

#### Scenario: Check allows any change without active orders
- **WHEN** `TrainStationChangeChecker.check()` is called with hasActiveOrders=false
- **THEN** the checker SHALL return `ALLOWED` regardless of the structural change type
