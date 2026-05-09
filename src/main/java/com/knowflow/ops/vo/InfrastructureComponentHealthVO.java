package com.knowflow.ops.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InfrastructureComponentHealthVO {

    private String key;
    private String name;
    private String type;
    private Boolean enabled;
    private String status;
    private String description;
    private String actionText;
    private String actionUrl;
}
