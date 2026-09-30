package com.organization.application.configurations.seeder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IRoleRepository;
import com.organization.application.repositories.IUserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("BootstrapAdminInitializer")
class BootstrapAdminInitializerTests {

    private static final String EMAIL = "root@organization.test";

    private static final String PASSWORD = "AdminPassword1";

    private static final String ENCODED_PASSWORD = "encoded:" + PASSWORD;

    private static final String PASSWORD_POLICY_ERROR =
            "Bootstrap admin cannot run: app.bootstrap.admin.password must be between 12 and 72";

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Mock
    private IUserRepository userRepository;

    @Mock
    private IRoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Captor
    private ArgumentCaptor<UserEntity> savedUser;

    private RoleEntity adminRole;

    @BeforeEach
    void setUp() {
        adminRole = RoleEntity.builder().id(1).type(RoleType.ADMIN).build();
    }

    @Nested
    @DisplayName("when no admin exists")
    class WithoutAdmin {

        @Test
        @DisplayName("creates an active admin with the configured email")
        void createsTheAdmin() {
            givenNoAdmin();
            givenAvailableEmail();
            when(roleRepository.findByType(RoleType.ADMIN)).thenReturn(Optional.of(adminRole));
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);

            initializer().run();

            verify(userRepository).save(savedUser.capture());
            UserEntity admin = savedUser.getValue();
            assertEquals(EMAIL, admin.getEmail(), "the configured email is the one stored");
            assertTrue(admin.isActive(), "the bootstrap admin must be able to log in right away");
            assertEquals(1, admin.getRoleEntities().size(), "the bootstrap account is an admin only");
            assertEquals(RoleType.ADMIN, admin.getRoleEntities().iterator().next().getType(),
                    "the single role has to be ADMIN");
        }

        @Test
        @DisplayName("stores the encoded password, never the raw one")
        void encodesThePassword() {
            givenNoAdmin();
            givenAvailableEmail();
            when(roleRepository.findByType(RoleType.ADMIN)).thenReturn(Optional.of(adminRole));
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);

            initializer().run();

            verify(userRepository).save(savedUser.capture());
            assertEquals(ENCODED_PASSWORD, savedUser.getValue().getPassword(),
                    "the password must reach the database hashed");
        }

        @Test
        @DisplayName("refuses a password below the update policy")
        void rejectsShortPassword() {
            givenNoAdmin();
            when(roleRepository.findByType(RoleType.ADMIN)).thenReturn(Optional.of(adminRole));

            BootstrapAdminInitializer initializer = initializer("RootAdmin1");

            IllegalStateException failure =
                    assertThrows(IllegalStateException.class, initializer::run);
            assertTrue(failure.getMessage().contains(PASSWORD_POLICY_ERROR),
                    "the failure names the policy that was violated, got: "
                            + failure.getMessage());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses a password above the update policy")
        void rejectsLongPassword() {
            givenNoAdmin();
            when(roleRepository.findByType(RoleType.ADMIN)).thenReturn(Optional.of(adminRole));

            String tooLong = "a".repeat(73);
            BootstrapAdminInitializer initializer = initializer(tooLong);

            IllegalStateException failure =
                    assertThrows(IllegalStateException.class, initializer::run);
            assertTrue(failure.getMessage().contains(PASSWORD_POLICY_ERROR),
                    "73 characters is above the BCrypt limit of 72, got: "
                            + failure.getMessage());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses a password outside printable ASCII")
        void rejectsNonPrintablePassword() {
            givenNoAdmin();
            when(roleRepository.findByType(RoleType.ADMIN)).thenReturn(Optional.of(adminRole));

            BootstrapAdminInitializer initializer = initializer("Admin\tPassword1");

            IllegalStateException failure =
                    assertThrows(IllegalStateException.class, initializer::run);
            assertTrue(failure.getMessage().contains("printable ASCII"),
                    "a control character has no place in an operator secret, got: "
                            + failure.getMessage());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses an invalid email")
        void rejectsInvalidEmail() {
            givenNoAdmin();
            when(roleRepository.findByType(RoleType.ADMIN)).thenReturn(Optional.of(adminRole));

            BootstrapAdminInitializer initializer =
                    initializer("not-an-email", PASSWORD);

            IllegalStateException failure =
                    assertThrows(IllegalStateException.class, initializer::run);
            assertTrue(failure.getMessage().contains("the configured admin is invalid"),
                    "the bean validation on the entity owns the email rules, got: "
                            + failure.getMessage());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses an empty email")
        void rejectsEmptyEmail() {
            givenNoAdmin();
            when(roleRepository.findByType(RoleType.ADMIN)).thenReturn(Optional.of(adminRole));

            BootstrapAdminInitializer initializer = initializer("", PASSWORD);

            assertThrows(IllegalStateException.class, initializer::run);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("fails with a migrations hint when the ADMIN role is missing")
        void rejectsMissingAdminRole() {
            givenNoAdmin();
            when(roleRepository.findByType(RoleType.ADMIN)).thenReturn(Optional.empty());

            IllegalStateException failure =
                    assertThrows(IllegalStateException.class, () -> initializer().run());

            assertTrue(failure.getMessage().contains("V2__seed_roles.sql"),
                    "a missing ADMIN role means Flyway never seeded it, got: "
                            + failure.getMessage());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("when the environment is already provisioned")
    class WithAdmin {

        @Test
        @DisplayName("skips when an admin exists")
        void skipsWhenAnAdminExists() {
            when(userRepository.existsByRoleEntitiesType(RoleType.ADMIN)).thenReturn(true);

            initializer().run();

            verify(userRepository, never()).save(any());
            verify(roleRepository, never()).findByType(any());
        }

        @Test
        @DisplayName("skips when the email exists, without promoting it")
        void doesNotPromoteAnExistingUser() {
            when(userRepository.existsByRoleEntitiesType(RoleType.ADMIN)).thenReturn(false);
            when(roleRepository.findByType(RoleType.ADMIN)).thenReturn(Optional.of(adminRole));
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            initializer().run();

            verify(userRepository, never()).save(any());
            verify(passwordEncoder, never()).encode(any());
        }
    }

    @Nested
    @DisplayName("as a bean")
    class AsABean {

        private final ApplicationContextRunner contextRunner =
                new ApplicationContextRunner().withUserConfiguration(SeedersContext.class);

        @Test
        @DisplayName("stays absent while the flag is off")
        void staysAbsentWithoutTheFlag() {
            contextRunner.run(context -> assertTrue(
                    context.getBeansOfType(BootstrapAdminInitializer.class).isEmpty(),
                    "the bootstrap must not even be registered unless an operator opts in"));
        }

        @Test
        @DisplayName("registers when the flag is on")
        void registersWithTheFlag() {
            contextRunner
                    .withPropertyValues("app.bootstrap.admin.enabled=true",
                            "app.bootstrap.admin.email=" + EMAIL,
                            "app.bootstrap.admin.password=" + PASSWORD)
                    .run(context -> assertEquals(1,
                            context.getBeansOfType(BootstrapAdminInitializer.class).size(),
                            "an opted-in environment gets exactly one bootstrap runner"));
        }

        @Test
        @DisplayName("runs after the dev seeder, so the seeder still sees an empty users table")
        void runsAfterTheDevSeeder() {
            contextRunner
                    .withPropertyValues("app.bootstrap.admin.enabled=true",
                            "app.bootstrap.admin.email=" + EMAIL,
                            "app.bootstrap.admin.password=" + PASSWORD,
                            "spring.profiles.active=dev",
                            "app.seed.password=" + PASSWORD)
                    .run(context -> {
                        List<CommandLineRunner> runners =
                                new ArrayList<>(context.getBeansOfType(CommandLineRunner.class)
                                        .values());
                        AnnotationAwareOrderComparator.sort(runners);

                        assertEquals(UsersSeeder.class, runners.get(0).getClass(),
                                "the seeder skips its two accounts when the users table is not "
                                        + "empty, so it has to run first");
                        assertEquals(BootstrapAdminInitializer.class, runners.get(1).getClass(),
                                "the bootstrap is the last runner by design");
                    });
        }
    }

    private BootstrapAdminInitializer initializer() {
        return initializer(EMAIL, PASSWORD);
    }

    private BootstrapAdminInitializer initializer(String password) {
        return initializer(EMAIL, password);
    }

    private BootstrapAdminInitializer initializer(String email, String password) {
        return new BootstrapAdminInitializer(userRepository, roleRepository, passwordEncoder,
                validator, email, password);
    }

    private void givenNoAdmin() {
        when(userRepository.existsByRoleEntitiesType(RoleType.ADMIN)).thenReturn(false);
    }

    private void givenAvailableEmail() {
        when(userRepository.existsByEmail(eq(EMAIL))).thenReturn(false);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({UsersSeeder.class, BootstrapAdminInitializer.class})
    static class SeedersContext {

        @Bean
        IUserRepository userRepository() {
            return mock(IUserRepository.class);
        }

        @Bean
        IRoleRepository roleRepository() {
            return mock(IRoleRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }
    }
}