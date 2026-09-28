package com.organization.application.dtos.response;

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
}
