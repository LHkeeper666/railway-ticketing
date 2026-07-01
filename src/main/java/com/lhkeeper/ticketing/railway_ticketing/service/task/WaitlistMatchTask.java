package com.lhkeeper.ticketing.railway_ticketing.service.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Waitlist;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.WaitlistStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.mapper.WaitlistMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.WaitlistService;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistMatchTask {

    private final WaitlistMapper waitlistMapper;
    private final WaitlistService waitlistService;
    private final DistributedLockFactory lockFactory;

    @Scheduled(fixedDelay = 30_000)
    public void matchWaitingOrders() {
        DistributedLock lock = lockFactory.tryLock("task:waitlist:match", 25);
        if (lock == null) {
            return;
        }
        try {
            List<Waitlist> waitingList = waitlistMapper.selectList(
                    Wrappers.lambdaQuery(Waitlist.class)
                            .eq(Waitlist::getStatus, WaitlistStatusEnum.WAITING.getCode())
                            .orderByAsc(Waitlist::getCreateTime)
                            .last("LIMIT 200")
            );
            if (waitingList.isEmpty()) {
                return;
            }

            // 按 (trainId, seatType, startStation, endStation) 分组
            Map<String, List<Waitlist>> grouped = waitingList.stream().collect(Collectors.groupingBy(
                    w -> w.getTrainId() + ":" + w.getSeatType() + ":" + w.getStartStation() + ":" + w.getEndStation()
            ));

            for (Map.Entry<String, List<Waitlist>> entry : grouped.entrySet()) {
                Waitlist first = entry.getValue().get(0);
                try {
                    waitlistService.processWaitlist(first.getTrainId(), first.getSeatType(),
                            first.getStartStation(), first.getEndStation());
                } catch (Exception e) {
                    log.error("候补匹配任务异常, key={}", entry.getKey(), e);
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
