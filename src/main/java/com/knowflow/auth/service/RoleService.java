package com.knowflow.auth.service;

import com.knowflow.auth.entity.RoleEntity;
import com.knowflow.auth.vo.RoleVO;

import java.util.List;

public interface RoleService {

    List<RoleVO> listAssignableRoles();

    List<String> listRoleCodesByUserId(Long userId);

    List<RoleEntity> listRolesByIds(List<Long> roleIds);
}

