package com.organization.application.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_user")
    @Setter(AccessLevel.NONE)
    private Integer id;

    @NotBlank
    @Column(name = "first_name_user", nullable = false, length = 60)
    private String firstname;

    @NotBlank
    @Column(name = "last_name_user", nullable = false, length = 60)
    private String lastname;

    @NotBlank
    @Email
    @Column(name = "email_user", nullable = false, length = 80, unique = true)
    private String email;

    @NotBlank
    @Column(name = "password_user", nullable = false)
    private String password;

    @Column(name = "active_user", nullable = false)
    private boolean active;

    /**
     * SHA-256 en hexadecimal del token enviado por mail, nunca el token en claro.
     * No se destruye al activarse la cuenta: se conserva para poder distinguir
     * "token ya usado" (409) de "token desconocido" (400). La garantía de un solo uso
     * viene de {@link #isPendingActivation()} (activado_at_user deja de ser null).
     */
    @Column(name = "activation_token_user", length = 64, unique = true)
    private String activationToken;

    @Column(name = "activation_expires_at_user")
    private Timestamp activationExpiresAt;

    /**
     * Cuándo confirmó el usuario su registro por primera vez. Es lo que distingue una cuenta
     * pendiente de una suspendida por un administrador; ver {@link #isPendingActivation()}.
     */
    @Column(name = "activated_at_user")
    private Timestamp activatedAt;

    @Column(name = "create_at_user")
    @CreationTimestamp
    private Timestamp createAt;

    @Column(name = "update_at_user")
    @UpdateTimestamp
    private Timestamp updateAt;

    @NotNull
    @ManyToMany(fetch = FetchType.EAGER, targetEntity = RoleEntity.class)
    @JoinTable(
            name = "users_roles",
            joinColumns = @JoinColumn(name = "id_user"),
            inverseJoinColumns = @JoinColumn(name = "id_role")
    )
    private Set<RoleEntity> roleEntities;

    /**
     * Una sola fuente para distinguir "registro sin confirmar" de "cuenta suspendida". Todo
     * activo tiene {@code activatedAt} puesto, así que {@code null} significa exactamente una
     * cosa: creado por el registro público y todavía sin confirmar.
     *
     * @return true si la cuenta nunca fue confirmada
     */
    public boolean isPendingActivation() {
        return activatedAt == null;
    }
}
