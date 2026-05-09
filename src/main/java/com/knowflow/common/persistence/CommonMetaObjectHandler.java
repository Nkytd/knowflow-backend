package com.knowflow.common.persistence;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CommonMetaObjectHandler implements MetaObjectHandler {

    private final CurrentUserProvider currentUserProvider;

    public CommonMetaObjectHandler(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdBy", Long.class, currentUser.userId());
        strictInsertFill(metaObject, "updatedBy", Long.class, currentUser.userId());
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        strictUpdateFill(metaObject, "updatedBy", Long.class, currentUser.userId());
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}

