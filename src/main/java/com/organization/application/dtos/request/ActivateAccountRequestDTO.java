package com.organization.application.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Canje del token de activación por la contraseña definitiva.
 *
 * <p>La política es la misma que en {@link UpdateUserRequestDTO} y que en
 * {@code BootstrapAdminInitializer}, y está triplicada a propósito: {@code save()} no corre
 * validación de bean, así que cada copia cubre un camino distinto. Si se cambia una, cambian
 * las tres.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ActivateAccountRequestDTO {

    @NotBlank
    private String token;

    @NotBlank
    @Pattern(regexp = "^[\\x20-\\x7E]+$")
    @Size(min = 12, max = 72)
    private String password;
}