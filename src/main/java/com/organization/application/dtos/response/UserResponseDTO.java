package com.organization.application.dtos.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@JsonRootName("user")
@Getter
@Setter
@Builder
public class UserResponseDTO {

    private Integer id;

    private String firstname;

    private String lastname;

    private String email;

    @JsonProperty("roles")
    private Set<RoleResponseDTO> roles;

    private boolean active;

}
