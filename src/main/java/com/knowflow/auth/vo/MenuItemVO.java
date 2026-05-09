package com.knowflow.auth.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MenuItemVO {

    private String name;
    private String path;
}

