package com.organization.application.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro público. No lleva contraseña ni rol a propósito: la contraseña la elige el usuario
 * en el paso de activación y el rol es siempre USER, así que aceptarlos acá abriría la puerta
 * a que cualquiera se auto-asigne ADMIN.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class RegisterUserRequestDTO {

    @NotBlank
    private String firstname;

    @NotBlank
    private String lastname;

    @Email
    @NotBlank
    private String email;
}