## ADDED Requirements

### Requirement: Admin can manage regions
The system SHALL provide CRUD endpoints under `/admin/region` for managing regions (cities/areas).

#### Scenario: List regions with pagination
- **WHEN** an admin sends `GET /admin/region/page?current=1&size=20&keyword=北京`
- **THEN** the system SHALL return a paginated list of regions matching the keyword

#### Scenario: Get region detail
- **WHEN** an admin sends `GET /admin/region/{id}`
- **THEN** the system SHALL return the region details including name, full_name, code, initial, spell

#### Scenario: Create a new region
- **WHEN** an admin sends `POST /admin/region` with valid region data
- **THEN** the system SHALL insert the region into `t_region` and return the created region

#### Scenario: Update a region
- **WHEN** an admin sends `PUT /admin/region/{id}` with updated region data
- **THEN** the system SHALL update the region and invalidate the region Redis cache

#### Scenario: Delete a region
- **WHEN** an admin sends `DELETE /admin/region/{id}`
- **THEN** the system SHALL check if the region is referenced by any station or train; if not, delete it; if yes, return error with message

### Requirement: Admin can manage stations
The system SHALL provide CRUD endpoints under `/admin/station` for managing train stations.

#### Scenario: List stations with pagination
- **WHEN** an admin sends `GET /admin/station/page?current=1&size=20&keyword=北京`
- **THEN** the system SHALL return a paginated list of stations matching the keyword

#### Scenario: Get station detail
- **WHEN** an admin sends `GET /admin/station/{id}`
- **THEN** the system SHALL return the station details including station_code, station_name, region, spell

#### Scenario: Create a new station
- **WHEN** an admin sends `POST /admin/station` with valid station data (station_code, station_name, region_code, region_name, spell)
- **THEN** the system SHALL insert the station into `t_station` and return the created station

#### Scenario: Update a station
- **WHEN** an admin sends `PUT /admin/station/{id}` with updated station data
- **THEN** the system SHALL update the station details

#### Scenario: Delete a station referenced by train routes
- **WHEN** an admin sends `DELETE /admin/station/{id}` and the station is referenced by any `t_train_station` record
- **THEN** the system SHALL return error with message "该站点被列车路线引用，无法删除"

#### Scenario: Delete an unreferenced station
- **WHEN** an admin sends `DELETE /admin/station/{id}` and the station is not referenced by any train
- **THEN** the system SHALL delete the station
