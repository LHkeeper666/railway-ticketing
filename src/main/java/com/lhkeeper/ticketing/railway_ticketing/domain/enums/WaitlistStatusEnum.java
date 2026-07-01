package com.lhkeeper.ticketing.railway_ticketing.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum WaitlistStatusEnum {

    WAITING(0),
    MATCHED(1),
    EXPIRED(2),
    CANCELED(3);

    private final Integer code;
}
