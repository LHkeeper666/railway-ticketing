## Context

当前项目是一个铁路售票系统单体应用，所有核心数据（列车、站点、座位、票价）通过 `db_data.sql` 手工填充。用户体系无角色区分，JWT 只携带 `userId/username/phone`。station bitmap 使用段索引作为 bit 位——插入/删除中间站会导致所有后续 bit 位移位，使已有 `seat_bitmap` 和 `purchaseMask` 数据语义错乱。

## Goals / Non-Goals

**Goals:**
- 提供完整的后台管理 REST API，覆盖站点、区域、列车、订单、用户五大模块
- 实现最小改动的角色体系（`t_user.role` + JWT claim + `@AdminRequired` 拦截器）
- 设计安全的路线变更机制：冻结+克隆，防止 seat_bitmap 位图错乱
- 实现 `TrainStationRelation` 自动生成，替代手工 SQL
- `t_seat.price` DB 列标记废弃，确立 `t_train_station_price` 为唯一票价来源
- 分三阶段实现，每阶段可独立交付

**Non-Goals:**
- 不做完整 RBAC（多角色、权限粒度化）
- 不做前端管理界面（纯 REST API）
- 不做列车时刻表版本化系统（保持冻结+克隆的简单方案）
- 不删除 `t_seat.price` DB 列（向后兼容，仅标记废弃）

## Decisions

### 1. 角色体系：单字段 + 拦截器，不做 RBAC

**选择**: `t_user` 加 `role TINYINT DEFAULT 0`（0=user, 1=admin），JWT 增加 `role` claim，`AdminInterceptor`（order=2）拦截 `/admin/**` 校验 role=1。

**Alternatives considered**:
- RBAC（user/role/permission 三表）：面试项目过重，一个 role 字段足够证明"我知道怎么做权限控制"
- Spring Security：项目当前用自研 JWT 拦截器体系，引入 Spring Security 会大幅改变架构

### 2. 路线变更安全：冻结 + 克隆 + 变更检测

**选择**: 有活跃订单时，路线结构变更（中间插入/删除/重排）通过克隆列车完成；无订单时允许原地修改；末尾追加/删除允许（安全检查通过时）；纯元数据修改（时间/名称）始终允许。变更检测由 `TrainStationChangeChecker` 负责。

**Alternatives considered**:
- 路线版本化（`t_train_station_version` 新表）：更完整但复杂度显著增加，面试项目不合适
- 段 ID 位图（用全局唯一 segment_id 代替索引位）：需要重构底层位图逻辑，影响面太大

**变更检测矩阵**:

| 操作 | 无历史订单 | 有历史订单 |
|------|-----------|-----------|
| 末尾追加 | ✅ | ✅ |
| 末尾删除 | ✅ | ⚠️ 检查无 ticket 覆盖该段 |
| 中间插入 | ✅ | ❌ 禁止 → 引导克隆 |
| 中间删除 | ✅ | ❌ 禁止 → 引导克隆 |
| 修改元数据 | ✅ | ✅ |

**为何末尾追加安全**：新 bit 位在已有 seat_bitmap 中天然为 0，旧 purchaseMask 不变，`(seat_bitmap & mask) = 0` 检查不受影响。

**克隆流程**:
```
cloneTrain(oldId, request):
  ├─ 1. 查原 Train
  ├─ 2. 雪花生成 newTrainId
  ├─ 3. INSERT t_train (newId, 管理员指定的 trainNumber, sale_status=0)
  ├─ 4. INSERT t_train_station (新路线，序列重新编号)
  ├─ 5. INSERT t_carriage (复制，train_id=newId)
  ├─ 6. INSERT t_seat (复制，train_id=newId, seat_bitmap=0, seat_status=AVAILABLE)
  ├─ 7. generateRelations(newId) → INSERT t_train_station_relation
  ├─ 8. generatePrices(newId) → INSERT t_train_station_price
  ├─ 9. UPDATE t_train SET sale_status=1 WHERE id=oldId   ← 冻结旧车
  └─ 10. 清除 Redis 缓存
```

全部在一个 `@Transactional` 中，失败回滚（旧车 sale_status 也不会变）。

### 3. 候补队列：不自动转移

**选择**: 冻结列车时，旧候补留在原 `train_id`，旧车退票仍触发 `triggerMatch()` 匹配旧候补。新候补自然流向 `sale_status=0` 的新列车。

**理由**: 新列车路线与旧车不同（bitmap 结构变了），候补请求的起止站在新旧路线下对应的 mask 可能不同，转移会产生隐蔽 bug。且新列车没有已售座位，"候补等退票"的场景不存在。

### 4. 晚点接口独立于时刻表调整

**选择**: `POST /admin/train/{id}/delay` 独立于 `PUT /admin/train/{id}`，语义不同。

| | 时刻表调整 | 运营晚点 |
|---|---|---|
| 触发时机 | 新时刻表发布 | 当天实时调整 |
| 影响 | 未来售票 | 已购票乘客行程 |
| 通知 | 否 | 应通知受影响乘客 |
| 退款 | 否 | 可触发免费退票 |
| 记录 | 覆盖更新 | 建议记录 history |

当前阶段两个接口的 DB 操作相同（更新时间字段），但独立接口为后续扩展（通知、退票联动）留出空间。

### 5. `t_seat.price` 废弃

**选择**: `t_seat.price` DB 列保留不动（避免数据迁移风险），但所有读写绕过它：
- 票价查询：统一走 `t_train_station_price(train_id, start, end, seat_type)`
- `TicketServiceImpl.setSeatPrices()` 在内存中覆盖 `seat.price`
- `SeatSelector.getPrice()` 查 `t_train_station_price`
- 新增/修改票价时只写 `t_train_station_price`

**理由**: 同一座位在不同区间下价格不同（北京南→济南西 ¥195 vs 北京南→宁波 ¥617），存一个固定值无意义。现实中 12306 票价由"运营里程 × 递远递减费率"决定，不由座位决定。

### 6. TrainStationRelation 自动生成

**选择**: 每次路线变更后全量重建——先 DELETE 旧记录，再 INSERT 全部起止站组合 `C(n,2)`。

```java
for (int i = 0; i < stations.size(); i++) {
    for (int j = i + 1; j < stations.size(); j++) {
        // insert (trainId, stations[i].name, stations[j].name, ...)
    }
}
```

**理由**: 当前数据是 SQL 手工填充的，路线变更后不自动重构会导致查询不到新增的起止站组合。

## Risks / Trade-offs

- **[冻结后旧车数据残留]**：旧列车 `sale_status=1` 后仍有 seat_bitmap 占用和未完成订单，这些数据会一直存在直到所有订单完结。→ 为管理后台提供"可归档检测"（所有订单 CANCELED/COMPLETED/REFUNDED + 所有候补 EXPIRED/CANCELED）。
- **[克隆列车 price 缺失]**：克隆时新段的 `t_train_station_price` 价格默认为 0，需管理员手动补全。→ 价格管理页面标红提示未定价区间。
- **[冻结合并问题]**：如果旧车和新车 `train_number` 相同（都是 "G35"），前端余票查询展示可能混淆。→ 余票查询加 `sale_status=0` 过滤；订单详情用 `train_id` 而非 `train_number` 关联。
- **[waitlist 候补用户可能永远等不到旧车退票]**：冻结后旧车不再有新票售出，退票概率降低。→ 接受此 trade-off，后续可考虑手动通知候补用户关注新车次。

## Open Questions

- 列车删除策略：物理删除还是仅软删除？建议——无订单时物理删除（含关联 seat/carriage），有订单时仅 `delFlag=1`。
- 座位批量生成规则：克隆时是复制旧座位的 `seat_number` 还是按 `carriage.seat_count` 重新生成？建议按车厢配置重新生成，确保段数变化后 `seat_bitmap` 起点一致。
