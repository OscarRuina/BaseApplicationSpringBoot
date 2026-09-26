package com.organization.application.converters;

import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.models.entities.UserEntity;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component("userConverter")
public class UserConverter {

    private final RoleConverter roleConverter;

    public UserConverter(RoleConverter roleConverter) {
        this.roleConverter = roleConverter;
    }

    public UserResponseDTO userToUserResponseDTO(UserEntity userEntity){
        return UserResponseDTO.builder()
                .id(userEntity.getId())
                .firstname(userEntity.getFirstname())
                .lastname(userEntity.getLastname())
                .email(userEntity.getEmail())
                .roles(userEntity.getRoleEntities().stream()
                        .map(roleConverter::roleToRoleResponseDTO)
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
                        .map(roleConverter::roleToRoleResponseDTO)
                        .collect(Collectors.toSet()))
                .active(userEntity.isActive())
                .token(token)
                .build();
    }
}
