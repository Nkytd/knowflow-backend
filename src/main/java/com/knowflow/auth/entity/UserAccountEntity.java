package com.knowflow.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("user_account")
@EqualsAndHashCode(callSuper = true)
public class UserAccountEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String username;
    private String passwordHash;
    private String realName;
    private String email;
    private String phone;
    private Integer age;
    private String gender;
    private String status;
    private LocalDateTime lastLoginAt;
}