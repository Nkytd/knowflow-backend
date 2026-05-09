package com.knowflow.ops.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpsHealthIssueVO {

    private String code;
    private String severity;
    private String title;
    private String description;
    private String actionText;
    private String actionUrl;
}
