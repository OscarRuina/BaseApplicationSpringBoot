package com.organization.application.converters;

import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.dtos.response.RoleResponseDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.models.entities.UserEntity;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("userConverter")
public class UserConverter {

    @Autowired
    private RoleConverter roleConverter;

    public UserResponseDTO userToUserResponseDTO(UserEntity userEntity){
        return UserResponseDTO.builder()
                .id(userEntity.getId())
                .firstname(userEntity.getFirstname())
                .lastname(userEntity.getLastname())
                .email(userEntity.getEmail())
                .roles(userEntity.getRoleEntities().stream()
                        .map(roleEntity -> roleConverter.roleToRoleResponseDTO(roleEntity))
                            .collect(Collectors.toSet()))
                .active(userEntity.isActive())
                .build();
    }

    public LoginResponseDTO userToLoginResponseDTO(UserEntity userEntity, String token){
        return LoginResponseDTO.builder()
                .id(userEntity.getId())
                .firstname(userEntity.getFirstname())
                .lastname(userEntity.getLastname())
                .email(userEntity.getEmail())
                .roles(userEntity.getRoleEntities().stream()
                        .map(roleEntity -> roleConverter.roleToRoleResponseDTO(roleEntity))
                        .collect(Collectors.toSet()))
                .active(userEntity.isActive())
                .token(token)
                .build();
    }

}
