package com.organization.application.converters;

import com.organization.application.dtos.response.RoleResponseDTO;
import com.organization.application.models.entities.RoleEntity;
import org.springframework.stereotype.Component;

@Component("roleConverter")
public class RoleConverter {

    public RoleResponseDTO roleToRoleResponseDTO(RoleEntity roleEntity){
        return RoleResponseDTO.builder()
                .id(roleEntity.getId())
                .role(roleEntity.getType().name())
                .build();
    }

}
