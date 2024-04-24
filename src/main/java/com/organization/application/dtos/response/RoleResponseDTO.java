package com.organization.application.dtos.response;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Builder;
import lombok.Getter;

@JsonRootName("role")
@Getter
@Builder
public class RoleResponseDTO {

    private Integer id;

    private String role;
}
