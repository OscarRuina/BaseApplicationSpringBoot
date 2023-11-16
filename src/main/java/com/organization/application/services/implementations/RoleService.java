package com.organization.application.services.implementations;

import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IRoleRepository;
import com.organization.application.services.interfaces.IRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RoleService implements IRoleService {

    @Autowired
    private IRoleRepository roleRepository;

    @Override
    public RoleEntity findRoleByType(RoleType roleType) {
        return roleRepository.findByType(roleType).get();
    }
}
