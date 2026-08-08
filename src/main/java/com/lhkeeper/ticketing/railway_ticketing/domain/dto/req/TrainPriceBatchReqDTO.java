package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

import java.util.List;

@Data
public class TrainPriceBatchReqDTO {

    private List<PriceItem> prices;

    @Data
    public static class PriceItem {
        private String startStation;
        private String endStation;
        private Integer seatType;
        private Integer price;
    }
}
