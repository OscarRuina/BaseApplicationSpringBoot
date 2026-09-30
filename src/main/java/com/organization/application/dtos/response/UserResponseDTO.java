package com.organization.application.dtos.response;

import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponseDTO {

    private Integer id;

    private String firstname;

    private String lastname;

    private String email;

    private Set<RoleResponseDTO> roles;

    private boolean active;

    /**
     * Cuándo confirmó el usuario su registro. Ausente (null) significa registro pendiente de
     * confirmación por email; en cualquier otro caso indica que la cuenta estuvo activa en
     * algún momento, aunque hoy {@code active} valga false por una suspensión.
     */
    private Instant activatedAt;
}
