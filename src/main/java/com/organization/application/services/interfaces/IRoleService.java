package com.organization.application.services.interfaces;

import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.enums.RoleType;

public interface IRoleService {

    RoleEntity findRoleByType(RoleType roleType);
}
