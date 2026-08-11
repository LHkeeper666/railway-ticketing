package com.lhkeeper.ticketing.railway_ticketing.domain.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态枚举，内置状态转移规则矩阵。
 */
public enum OrderStatusEnum {

    /** 未支付 */
    UNPAID(0),
    /** 已支付 */
    PAID(1),
    /** 已取消（终态） */
    CANCELED(2),
    /** 排队中（抢票） */
    PENDING(3),
    /** 候补中 */
    WAITLIST(4);

    private final Integer code;

    OrderStatusEnum(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return this.code;
    }

    /**
     * 状态转移规则：一条规则 = (目标状态, 触发事件)
     */
    public record Transition(OrderStatusEnum target, OrderStateEvent event) {}

    /** 转移规则矩阵，每个状态持有自己允许的转移规则集合 */
    private static final Map<OrderStatusEnum, Set<Transition>> TRANSITIONS = new EnumMap<>(OrderStatusEnum.class);

    static {
        TRANSITIONS.put(PENDING, Set.of(
                new Transition(UNPAID, OrderStateEvent.FLASH_SUCCEED),
                new Transition(CANCELED, OrderStateEvent.FLASH_FAIL),
                new Transition(CANCELED, OrderStateEvent.CANCEL),
                new Transition(CANCELED, OrderStateEvent.TIMEOUT_CANCEL)
        ));
        TRANSITIONS.put(WAITLIST, Set.of(
                new Transition(UNPAID, OrderStateEvent.WAITLIST_MATCH),
                new Transition(CANCELED, OrderStateEvent.CANCEL),
                new Transition(CANCELED, OrderStateEvent.TIMEOUT_CANCEL)
        ));
        TRANSITIONS.put(UNPAID, Set.of(
                new Transition(PAID, OrderStateEvent.PAY_NOTIFY),
                new Transition(CANCELED, OrderStateEvent.CANCEL),
                new Transition(CANCELED, OrderStateEvent.TIMEOUT_CANCEL)
        ));
        TRANSITIONS.put(PAID, Set.of(
                new Transition(CANCELED, OrderStateEvent.CANCEL),
                new Transition(CANCELED, OrderStateEvent.REFUND_ALL)
        ));
        TRANSITIONS.put(CANCELED, Collections.emptySet());
    }

    /**
     * 校验从当前状态到目标状态的转移是否合法。
     * @param targetCode 目标状态 code
     * @param event 触发事件
     * @return true=合法转移
     */
    public boolean canTransitTo(Integer targetCode, OrderStateEvent event) {
        OrderStatusEnum target = fromCode(targetCode);
        if (target == null) {
            return false;
        }
        Set<Transition> allowed = TRANSITIONS.getOrDefault(this, Collections.emptySet());
        return allowed.contains(new Transition(target, event));
    }

    /**
     * 根据 code 获取枚举值。
     */
    public static OrderStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
