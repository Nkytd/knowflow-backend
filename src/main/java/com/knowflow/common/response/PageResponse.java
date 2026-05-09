package com.knowflow.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private Integer pageNo;
    private Integer pageSize;
    private Long total;
    private List<T> records;

    public static <T> PageResponse<T> of(Integer pageNo, Integer pageSize, Long total, List<T> records) {
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    public static <T> PageResponse<T> empty(Integer pageNo, Integer pageSize) {
        return new PageResponse<>(pageNo, pageSize, 0L, Collections.emptyList());
    }
}

