## Why

当前项目完全没有管理后台——列车、站点、座位、票价等核心数据全部靠 SQL 脚本手工填充，没有角色体系，任何登录用户权限均等。面试场景中，"数据怎么管理"、"管理员怎么操作"是必问题，缺乏管理后台会让项目显得不完整。

## What Changes

- **新增角色体系**：`t_user` 增加 `role` 字段，JWT 增加 role claim，新增 `@AdminRequired` 注解 + `AdminInterceptor` 拦截 `/admin/**` 路径
- **新增站点/区域管理**：`/admin/station`、`/admin/region` 完整 CRUD
- **新增列车管理**：`/admin/train` 完整 CRUD，含路线配置、车厢/座位/价格管理
- **新增列车路线变更安全机制**：有活跃订单时冻结路线 → 克隆列车工作流，防止 seat_bitmap 位图错乱；无订单时允许原地修改
- **新增 TrainStationRelation 自动生成**：根据 TrainStation 有序列表自动生成所有起止站直达关系，替代手工 SQL
- **新增订单管理（只读 + 干预）**：`/admin/order` 分页查询 + 手动取消/退票
- **新增用户管理**：`/admin/user` 分页查询 + 禁用/启用
- **新增列车晚点接口**：`POST /admin/train/{id}/delay`，语义独立于时刻表调整
- **`t_seat.price` 列标记废弃**：DB 层价格列冗余（实际票价由 `t_train_station_price` 决定），`Seat.price` 保留为瞬时载体字段

## Capabilities

### New Capabilities
- `admin-auth`: 管理员认证与鉴权 — role 字段、AdminRequired 注解、AdminInterceptor
- `station-management`: 站点与区域 CRUD，含缓存刷新
- `train-management`: 列车 CRUD、路线配置、车厢/座位/价格管理、路线变更安全检查、克隆列车、TrainStationRelation 自动生成
- `order-management`: 订单后台查询与手动干预（取消/退票）
- `user-management`: 用户后台查询与状态管理（启用/禁用）

### Modified Capabilities
<!-- None - all capabilities are new -->

## Impact

- **DB 变更**: `t_user` 加 `role` 列；`t_seat.price` 标记废弃（不立即删除，向后兼容）
- **新增 Controller**: `admin/` 包下 5 个 AdminController
- **新增 Service**: `admin/` 包下 5 个 AdminService + `TrainStationRelationGenerator`、`TrainStationChangeChecker` 辅助类
- **新增 Interceptor**: `AdminInterceptor`（order=2，在 JwtInterceptor 之后）
- **新增 Annotation**: `@AdminRequired`
- **JWT 变更**: 签发时增加 `role` claim
- **UserInfo 变更**: 增加 `role` 字段
- **缓存影响**: 区域/站点/列车变更时需刷新对应 Redis key
- **候补队列**: 冻结列车时旧候补留在原 train_id，不自动转移；旧车退票仍可匹配旧候补
