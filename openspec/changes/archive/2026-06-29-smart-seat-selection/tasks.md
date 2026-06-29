## 1. 基础设施

- [x] 1.1 创建 SeatMatrixDTO 类 (domain/dto/SeatMatrixDTO.java)
- [x] 1.2 创建 SelectedSeatDTO 类 (domain/dto/SelectedSeatDTO.java)
- [x] 1.3 创建 CarriageInfo 类 (domain/dto/CarriageInfo.java)
- [x] 1.4 创建 SeatNumberParser 工具类 (util/SeatNumberParser.java)
- [x] 1.5 修改 OrderCreateReqDTO，添加 chooseSeats 字段

## 2. 座位矩阵构建

- [x] 2.1 实现 SeatNumberParser.parse() 座位号解析方法
- [x] 2.2 实现 SeatNumberParser.getPositionIndex() 位置索引映射
- [x] 2.3 实现 SeatNumberParser.isValidPosition() 位置有效性校验
- [x] 2.4 实现 SeatSelector.buildSeatMatrix() 矩阵构建方法
- [x] 2.5 实现 SeatSelector.buildCarriageInfoMap() 按车厢分组构建

## 3. 选座算法

- [x] 3.1 实现 findAdjacentWithPreference() 优先级1: 同排+满足偏好
- [x] 3.2 实现 findAdjacent() 优先级2: 同排连续座位
- [x] 3.3 实现 findSameRow() 优先级3: 同排分散座位
- [x] 3.4 实现 findAny() 优先级4: 任意可用座位
- [x] 3.5 实现 findSeatsInCarriage() 单车厢选座组合方法
- [x] 3.6 实现 selectSeats() 主选座方法 (单车厢优先，失败跨车厢)

## 4. 跨车厢选座

- [x] 4.1 实现 selectAcrossCarriages() 跨车厢选座方法
- [x] 4.2 实现车厢排序逻辑 (按可用座位数降序)
- [x] 4.3 实现偏好处理策略 (只在第一个车厢尝试)

## 5. 分布式锁集成

- [x] 5.1 修改 selectAndLockSeats()，添加分布式锁获取
- [x] 5.2 实现锁 key 生成逻辑 (trainId + seatType)
- [x] 5.3 实现锁超时处理和异常释放

## 6. 批量锁定

- [x] 6.1 实现 batchLockSeats() 批量更新方法
- [x] 6.2 实现 convertToSelectedSeats() 矩阵坐标转座位信息
- [x] 6.3 实现 fillTicketDTO() 填充 TicketDTO 结果

## 7. 缓存处理

- [x] 7.1 实现 invalidateStockCache() 删除余票缓存
- [x] 7.2 修改 queryAvailableSeats() 直接查数据库

## 8. 参数校验

- [x] 8.1 创建 OrderCreateSeatTypeChainHandler (座位类型一致性校验)
- [x] 8.2 创建 OrderCreateChooseSeatChainHandler (偏好数量和字符校验)
- [x] 8.3 注册校验器到责任链

## 9. 主入口重构

- [x] 9.1 重构 selectAndLockSeats() 主方法，整合所有逻辑
- [x] 9.2 修改 FlashOrderConsumer，传递 chooseSeats 参数
- [x] 9.3 修改 OrderServiceImpl，传递 chooseSeats 参数

## 10. 测试

- [x] 10.1 编写 SeatNumberParser 单元测试
- [x] 10.2 编写选座算法单元测试 (各优先级场景)
- [x] 10.3 编写跨车厢选座单元测试
- [ ] 10.4 编写集成测试 (完整选座流程)
- [ ] 10.5 编写并发测试 (分布式锁验证)
