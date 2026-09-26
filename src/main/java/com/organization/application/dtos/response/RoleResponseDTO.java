package com.organization.application.dtos.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoleResponseDTO {

    private Integer id;

    private String role;
}
