package com.organization.application.dtos.response;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@JsonRootName("role")
@Getter
@Setter
@Builder
public class RoleResponseDTO {

    private Integer id;

    private String role;

}
