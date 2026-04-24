package com.lhkeeper.ticketing.railway_ticketing.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class TicketDTO {

    /**
     * 乘车人 ID
     */
    private String passengerId;

    /**
     * 乘车人姓名
     */
    private String realName;

    /**
     * 乘车人证件类型
     */
    private Integer idType;

    /**
     * 乘车人证件号
     */
    private String idCard;

    /**
     * 乘车人手机号
     */
    private String phone;

    /**
     * 用户类型 0：成人 1：儿童 2：学生 3：残疾军人
     */
    private Integer userType;

    /**
     * 席别类型
     */
    private Integer seatType;

    /**
     * 车厢号
     */
    private String carriageNumber;

    /**
     * 座位号
     */
    private String seatNumber;

    /**
     * 座位金额
     */
    private Integer amount;
}
