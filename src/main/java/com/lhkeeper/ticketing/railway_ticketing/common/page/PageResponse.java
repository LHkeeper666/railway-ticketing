package com.lhkeeper.ticketing.railway_ticketing.common.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private Long total;
    private Long current;
    private Long size;
    private List<T> records;

    public static <T> PageResponse<T> from(IPage<?> page, List<T> records) {
        return PageResponse.<T>builder()
                .total(page.getTotal())
                .current(page.getCurrent())
                .size(page.getSize())
                .records(records)
                .build();
    }

    public static <T> PageResponse<T> empty() {
        return PageResponse.<T>builder()
                .total(0L)
                .current(0L)
                .size(0L)
                .records(Collections.emptyList())
                .build();
    }
}
