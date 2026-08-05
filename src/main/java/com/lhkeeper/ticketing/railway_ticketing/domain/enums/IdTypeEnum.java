package com.lhkeeper.ticketing.railway_ticketing.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Objects;

@RequiredArgsConstructor
public enum IdTypeEnum {

    CHINESE_ID(1, "身份证"),
    PASSPORT(2, "护照"),
    HK_MACAO_PASS(3, "港澳通行证"),
    TAIWAN_PASS(4, "台胞证"),
    MILITARY_ID(5, "军人证"),
    DRIVING_LICENSE(6, "驾驶证");

    @Getter
    private final Integer code;

    @Getter
    private final String name;

    public static boolean isValidCode(Integer code) {
        return Arrays.stream(IdTypeEnum.values())
                .anyMatch(each -> Objects.equals(each.getCode(), code));
    }
}
