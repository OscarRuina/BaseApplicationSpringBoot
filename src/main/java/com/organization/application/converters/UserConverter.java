package com.organization.application.converters;

import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.models.entities.UserEntity;
import java.sql.Timestamp;
import java.time.Instant;
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
                .activatedAt(toInstant(userEntity.getActivatedAt()))
                .build();
    }

    /**
     * La entidad guarda un {@link java.sql.Timestamp} porque es el tipo que mapea el driver.
     * Convertir en el borde evita filtrar un tipo de JDBC al contrato JSON, donde Jackson lo
     * serializaría como epoch en lugar de una fecha legible.
     */
    private Instant toInstant(Timestamp activatedAt) {
        return activatedAt == null ? null : activatedAt.toInstant();
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
