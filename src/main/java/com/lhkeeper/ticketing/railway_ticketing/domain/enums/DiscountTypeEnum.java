package com.lhkeeper.ticketing.railway_ticketing.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Objects;

@RequiredArgsConstructor
public enum DiscountTypeEnum {

    ADULT(0, "成人全价"),
    CHILD(1, "儿童票"),
    STUDENT(2, "学生票"),
    DISABLED_SOLDIER(3, "残军票");

    @Getter
    private final Integer code;

    @Getter
    private final String name;

    public static boolean isValidCode(Integer code) {
        return Arrays.stream(DiscountTypeEnum.values())
                .anyMatch(each -> Objects.equals(each.getCode(), code));
    }
}
