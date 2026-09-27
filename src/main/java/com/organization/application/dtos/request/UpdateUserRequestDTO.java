package com.organization.application.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class UpdateUserRequestDTO {

    @NotBlank
    private String firstname;

    @NotBlank
    private String lastname;

    @Pattern(regexp = "^[\\x20-\\x7E]+$")
    @Size(min = 12, max = 72)
    private String password;
}
