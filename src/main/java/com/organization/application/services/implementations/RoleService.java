package com.organization.application.services.implementations;

import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IRoleRepository;
import com.organization.application.services.interfaces.IRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RoleService implements IRoleService {

    @Autowired
    private IRoleRepository roleRepository;

    @Override
    public RoleEntity findRoleByType(RoleType roleType) {
        return roleRepository.findByType(roleType).get();
    }
}
