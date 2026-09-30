package com.organization.application.configurations.seeder;

import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IRoleRepository;
import com.organization.application.repositories.IUserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea la cuenta ADMIN inicial en los entornos que no tienen ninguna, por ejemplo una base de
 * producción recién creada.
 *
 * <p>No es el override de activación por administrador que se descartó: nunca activa un
 * registro hecho por otra persona, y la contraseña la elige el operador antes de que la cuenta
 * exista. Es la única vía soportada para crear el primer admin, ya que el seeder es
 * {@code @Profile("dev")} y el registro público crea cuentas inactivas.
 *
 * <p>Corre como el último {@link CommandLineRunner}, después de {@link UsersSeeder}. El seeder
 * solo crea sus dos cuentas cuando la tabla de usuarios está vacía, así que un bootstrap que
 * corra primero dejaría al perfil dev sin su usuario normal.
 *
 * <p>La credencial queda vigente hasta que se cambie por {@code PUT /users}. La property no se
 * desactiva sola a propósito: el operador que la olvide recibe un WARN en cada arranque en
 * lugar de un fallo silencioso.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.bootstrap.admin.enabled", havingValue = "true")
public class BootstrapAdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private static final String FIRST_NAME = "Bootstrap";

    private static final String LAST_NAME = "Admin";

    /**
     * Espejo de la política que impone {@code @Valid} sobre {@code UpdateUserRequestDTO}. El
     * runner no pasa por el controller, y {@code userRepository.save()} no corre bean
     * validation sobre la entidad, así que sin esto el bootstrap aceptaría una contraseña de un
     * carácter. Hay que actualizar las dos pantallas juntas.
     */
    private static final int PASSWORD_MIN_LENGTH = 12;

    private static final int PASSWORD_MAX_LENGTH = 72;

    private static final Pattern PRINTABLE_ASCII = Pattern.compile("^[\\x20-\\x7E]+$");

    private final IUserRepository userRepository;

    private final IRoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    private final Validator validator;

    private final String email;

    private final String rawPassword;

    public BootstrapAdminInitializer(IUserRepository userRepository,
            IRoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            Validator validator,
            @Value("${app.bootstrap.admin.email}") String email,
            @Value("${app.bootstrap.admin.password}") String rawPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.email = email;
        this.rawPassword = rawPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByRoleEntitiesType(RoleType.ADMIN)) {
            log.warn("Bootstrap admin skipped: an ADMIN already exists. Remove the "
                    + "app.bootstrap.admin.* properties once the environment is provisioned.");
            return;
        }

        RoleEntity adminRole = roleRepository.findByType(RoleType.ADMIN).orElseThrow(
                () -> new IllegalStateException(
                        "Bootstrap admin cannot run: the ADMIN role is missing. Run the Flyway "
                                + "migrations, V2__seed_roles.sql seeds it."));

        UserEntity admin = UserEntity.builder()
                .firstname(FIRST_NAME)
                .lastname(LAST_NAME)
                .email(email)
                .password(rawPassword)
                .active(true)
                .roleEntities(Set.of(adminRole))
                .build();

        validate(admin);
        enforcePasswordPolicy(rawPassword);

        if (userRepository.existsByEmail(email)) {
            log.warn("Bootstrap admin skipped: {} already exists and was not promoted. "
                    + "Choose another app.bootstrap.admin.email.", email);
            return;
        }

        admin.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(admin);

        log.warn("Bootstrap admin {} created. This credential stays valid until it is changed "
                + "through PUT /users, and the app.bootstrap.admin.* properties should be removed.",
                email);
    }

    private void validate(UserEntity admin) {
        Set<ConstraintViolation<UserEntity>> violations = validator.validate(admin);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Bootstrap admin cannot run, the configured admin is invalid: " + violations);
        }
    }

    private void enforcePasswordPolicy(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH
                || password.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalStateException("Bootstrap admin cannot run: "
                    + "app.bootstrap.admin.password must be between " + PASSWORD_MIN_LENGTH + " and "
                    + PASSWORD_MAX_LENGTH + " characters.");
        }
        if (!PRINTABLE_ASCII.matcher(password).matches()) {
            throw new IllegalStateException("Bootstrap admin cannot run: "
                    + "app.bootstrap.admin.password must contain printable ASCII characters only.");
        }
    }
}
