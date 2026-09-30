package com.organization.application.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.organization.application.configurations.email.service.IEmailService;
import com.organization.application.configurations.exceptions.ActivationTokenAlreadyUsedException;
import com.organization.application.configurations.exceptions.CurrentPasswordInvalidException;
import com.organization.application.configurations.exceptions.CurrentPasswordRequiredException;
import com.organization.application.configurations.exceptions.ExpiredActivationTokenException;
import com.organization.application.configurations.exceptions.ForbiddenException;
import com.organization.application.configurations.exceptions.InvalidActivationTokenException;
import com.organization.application.configurations.exceptions.PendingActivationException;
import com.organization.application.configurations.exceptions.TooManyAttemptsException;
import com.organization.application.configurations.exceptions.UserAlreadyExistException;
import com.organization.application.configurations.exceptions.UserInactiveException;
import com.organization.application.configurations.exceptions.UserNotExistException;
import com.organization.application.configurations.security.throttle.LoginThrottle;
import com.organization.application.configurations.security.throttle.RegistrationThrottle;
import com.organization.application.converters.UserConverter;
import com.organization.application.dtos.request.ActivateAccountRequestDTO;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.request.UpdateUserRequestDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IUserRepository;
import com.organization.application.services.implementations.UserService;
import com.organization.application.services.interfaces.IRoleService;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    private static final String CALLER_EMAIL = "caller@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String CLIENT_IP = "203.0.113.10";
    private static final String ENCODED_SECRET = "encoded-secret";

    private static final Timestamp CONFIRMED_AT = new Timestamp(1_700_000_000_000L);

    @Mock
    private IUserRepository userRepository;

    @Mock
    private UserConverter userConverter;

    @Mock
    private IRoleService roleService;

    @Mock
    private IEmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginThrottle loginThrottle;

    @Mock
    private RegistrationThrottle registrationThrottle;

    private UserService userService;

    private static final String FRONTEND_BASE_URL = "https://app.example.com";

    private UserResponseDTO converted;

    @BeforeEach
    void setUp() {
        converted = mock(UserResponseDTO.class);
        // Se construye a mano en vez de con @InjectMocks: el constructor pide el frontend por
        // @Value, y Mockito no tiene con qué resolver un String, así que inyectaría null y el
        // link de activación saldría "null/activate?token=..." sin que ningún test lo note.
        userService = new UserService(userRepository, userConverter, roleService, emailService,
                passwordEncoder, loginThrottle, registrationThrottle, FRONTEND_BASE_URL);
    }

    private static RoleEntity role(RoleType type) {
        return RoleEntity.builder().type(type).build();
    }

    /**
     * Las fixtures son cuentas confirmadas. Una cuenta creada por un admin entra activada y una
     * que confirmó su registro tiene activatedAt; sin eso, el guard de pendientes las rechazaría
     * y los tests medirían la fixture equivocada en lugar de la regla de negocio.
     */
    private static UserEntity user(Integer id, String email, boolean active, RoleType... types) {
        return baseUser(id, email, active, CONFIRMED_AT, types);
    }

    /**
     * Registro público sin confirmar: inactivo y sin activatedAt. Inactivo por definición, por
     * eso el helper no acepta el flag.
     */
    private static UserEntity pendingUser(Integer id, String email, RoleType... types) {
        return baseUser(id, email, false, null, types);
    }

    private static UserEntity baseUser(Integer id, String email, boolean active,
            Timestamp activatedAt, RoleType... types) {
        Set<RoleEntity> roles = new HashSet<>();
        for (RoleType type : types) {
            roles.add(role(type));
        }
        return UserEntity.builder()
                .id(id)
                .firstname("Test")
                .lastname("User")
                .email(email)
                .password(ENCODED_SECRET)
                .active(active)
                .activatedAt(activatedAt)
                .roleEntities(roles)
                .build();
    }

    private void stubConversion(UserEntity entity) {
        when(userConverter.userToUserResponseDTO(entity)).thenReturn(converted);
    }

    @Nested
    @DisplayName("me")
    class Me {

        @Test
        @DisplayName("returns the caller profile")
        void returnsTheCallerProfile() {
            UserEntity entity = user(1, CALLER_EMAIL, true, RoleType.USER);
            when(userRepository.findByEmail(CALLER_EMAIL)).thenReturn(Optional.of(entity));
            stubConversion(entity);

            assertSame(converted, userService.me(CALLER_EMAIL));
        }

        @Test
        @DisplayName("throws when the caller no longer exists")
        void throwsWhenTheCallerIsGone() {
            when(userRepository.findByEmail(CALLER_EMAIL)).thenReturn(Optional.empty());

            assertThrows(UserNotExistException.class, () -> userService.me(CALLER_EMAIL));
        }
    }

    @Nested
    @DisplayName("register")
    class Register {

        private static final String CLIENT_IP = "198.51.100.7";

        private RegisterUserRequestDTO request() {
            return new RegisterUserRequestDTO("Test", "User", OTHER_EMAIL);
        }

        private void stubEmailIsFree() {
            when(userRepository.findByEmail(OTHER_EMAIL)).thenReturn(Optional.empty());
            when(roleService.findRoleByType(RoleType.USER)).thenReturn(role(RoleType.USER));
            when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_SECRET);
            when(userRepository.saveAndFlush(any(UserEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("persists an inactive user awaiting confirmation")
        void persistsAPendingUser() {
            stubEmailIsFree();

            userService.register(request(), CLIENT_IP);

            var captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).saveAndFlush(captor.capture());
            UserEntity saved = captor.getValue();
            assertFalse(saved.isActive(),
                    "a public registration cannot log in before confirming the mail");
            assertTrue(saved.isPendingActivation(),
                    "activatedAt must stay null or the account is not pending anymore");
            assertFalse(saved.getActivationToken().isBlank(),
                    "the row must store the token hash");
            assertNotNull(saved.getActivationExpiresAt(), "the token needs an expiry");
        }

        @Test
        @DisplayName("never stores a password the user could log in with")
        void storesAnUnusablePlaceholder() {
            stubEmailIsFree();

            userService.register(request(), CLIENT_IP);

            var captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).saveAndFlush(captor.capture());
            verify(passwordEncoder).encode(anyString());
            assertEquals(ENCODED_SECRET, captor.getValue().getPassword(),
                    "the column is NOT NULL, so it holds a random value the user never receives");
        }

        @Test
        @DisplayName("rejects an email that is already taken")
        void rejectsDuplicatedEmail() {
            when(userRepository.findByEmail(OTHER_EMAIL))
                    .thenReturn(Optional.of(user(9, OTHER_EMAIL, true, RoleType.USER)));

            assertThrows(UserAlreadyExistException.class,
                    () -> userService.register(request(), CLIENT_IP));
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("translates a concurrent insert into a conflict")
        void translatesConcurrentInsert() {
            when(userRepository.findByEmail(OTHER_EMAIL)).thenReturn(Optional.empty());
            when(roleService.findRoleByType(RoleType.USER)).thenReturn(role(RoleType.USER));
            when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_SECRET);
            doThrow(new DataIntegrityViolationException("duplicate key"))
                    .when(userRepository).saveAndFlush(any(UserEntity.class));

            assertThrows(UserAlreadyExistException.class,
                    () -> userService.register(request(), CLIENT_IP));
        }

        @Test
        @DisplayName("sends the activation mail with the raw token, not the hash")
        void sendsTheActivationMail() {
            stubEmailIsFree();

            userService.register(request(), CLIENT_IP);

            var captor = ArgumentCaptor.forClass(Map.class);
            verify(emailService).sendEmail(any(String[].class), anyString(), anyString(),
                    captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, Object> sent = captor.getValue();
            String link = String.valueOf(sent.get("activationLink"));
            assertTrue(link.contains("token="),
                    "the activation mail must carry a usable link: " + sent);
        }

        @Test
        @DisplayName("reissues a token that expired on an unconfirmed account")
        void reissuesAnExpiredToken() {
            UserEntity pending = pendingUser(9, OTHER_EMAIL, RoleType.USER);
            pending.setActivationToken("stale-hash");
            pending.setActivationExpiresAt(new Timestamp(System.currentTimeMillis() - 1_000));
            when(userRepository.findByEmail(OTHER_EMAIL)).thenReturn(Optional.of(pending));
            when(userRepository.saveAndFlush(pending)).thenReturn(pending);
            stubConversion(pending);

            userService.register(request(), CLIENT_IP);

            assertNotEquals("stale-hash", pending.getActivationToken(),
                    "a dead token must be replaced or the account is unreachable");
            verify(emailService).sendEmail(any(String[].class), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("refuses to reissue while the existing token is still valid")
        void doesNotReissueAValidToken() {
            UserEntity pending = pendingUser(9, OTHER_EMAIL, RoleType.USER);
            pending.setActivationExpiresAt(new Timestamp(System.currentTimeMillis() + 60_000));
            when(userRepository.findByEmail(OTHER_EMAIL)).thenReturn(Optional.of(pending));

            // Reenviarle el mismo enlace al dueño de la casilla sirve para bombardear el buzón
            // sin límite, así que sólo se renueva cuando el token ya no sirve.
            assertThrows(UserAlreadyExistException.class,
                    () -> userService.register(request(), CLIENT_IP));
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("spends throttle budget only when the request reaches the mail")
        void countsOnlyMailReachingAttempts() {
            when(userRepository.findByEmail(OTHER_EMAIL))
                    .thenReturn(Optional.of(user(9, OTHER_EMAIL, true, RoleType.USER)));

            assertThrows(UserAlreadyExistException.class,
                    () -> userService.register(request(), CLIENT_IP));
            verify(registrationThrottle, never()).recordAttempt(anyString());
        }

        @Test
        @DisplayName("counts a new registration against the throttle")
        void countsANewRegistration() {
            stubEmailIsFree();

            userService.register(request(), CLIENT_IP);

            verify(registrationThrottle).recordAttempt(CLIENT_IP);
        }

        @Test
        @DisplayName("refuses to start a registration while the client is throttled")
        void refusesWhenThrottled() {
            when(registrationThrottle.retryAfterSeconds(CLIENT_IP)).thenReturn(42L);

            assertThrows(TooManyAttemptsException.class,
                    () -> userService.register(request(), CLIENT_IP));
            verifyNoInteractions(emailService);
            verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        }
    }

    @Nested
    @DisplayName("activate")
    class Activate {

        private ActivateAccountRequestDTO request(String token) {
            return new ActivateAccountRequestDTO(token, "ValidPassw0rd!");
        }

        @Test
        @DisplayName("rejects a token that matches no registration")
        void rejectsAnUnknownToken() {
            when(userRepository.findByActivationToken(anyString())).thenReturn(Optional.empty());

            assertThrows(InvalidActivationTokenException.class,
                    () -> userService.activate(request("whatever")));
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("looks the token up by its hash, never by the raw value")
        void hashesTheTokenBeforeLookingUp() {
            when(userRepository.findByActivationToken(anyString())).thenReturn(Optional.empty());

            assertThrows(InvalidActivationTokenException.class,
                    () -> userService.activate(request("raw-token")));

            var captor = ArgumentCaptor.forClass(String.class);
            verify(userRepository).findByActivationToken(captor.capture());
            assertNotEquals("raw-token", captor.getValue(),
                    "persisting or querying the raw token would expose it in a dump");
        }

        @Test
        @DisplayName("confirms the account and stores the chosen password")
        void confirmsTheAccount() {
            UserEntity pending = pendingUser(3, OTHER_EMAIL, RoleType.USER);
            pending.setActivationExpiresAt(new Timestamp(System.currentTimeMillis() + 60_000));
            when(userRepository.findByActivationToken(anyString())).thenReturn(Optional.of(pending));
            when(passwordEncoder.encode("ValidPassw0rd!")).thenReturn(ENCODED_SECRET);
            when(userRepository.saveAndFlush(pending)).thenReturn(pending);
            stubConversion(pending);

            userService.activate(request("raw-token"));

            assertTrue(pending.isActive(), "activation must let the user in");
            assertFalse(pending.isPendingActivation(),
                    "activatedAt must be set or the API keeps calling it pending");
            assertEquals(ENCODED_SECRET, pending.getPassword());
        }

        @Test
        @DisplayName("rejects a spent token with a conflict")
        void rejectsASpentToken() {
            UserEntity alreadyActive = user(3, OTHER_EMAIL, true, RoleType.USER);
            alreadyActive.setActivationToken("used-hash");
            when(userRepository.findByActivationToken(anyString()))
                    .thenReturn(Optional.of(alreadyActive));

            assertThrows(ActivationTokenAlreadyUsedException.class,
                    () -> userService.activate(request("raw-token")));
            verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        }

        @Test
        @DisplayName("rejects an expired token with 410 and leaves the account pending")
        void rejectsAnExpiredToken() {
            UserEntity pending = pendingUser(3, OTHER_EMAIL, RoleType.USER);
            pending.setActivationExpiresAt(new Timestamp(System.currentTimeMillis() - 1_000));
            when(userRepository.findByActivationToken(anyString())).thenReturn(Optional.of(pending));

            assertThrows(ExpiredActivationTokenException.class,
                    () -> userService.activate(request("raw-token")));
            assertTrue(pending.isPendingActivation(),
                    "an expired token must not confirm the account");
            verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        }

        @Test
        @DisplayName("reports a spent token as spent even when it is also past its expiry")
        void checksUseBeforeExpiry() {
            // El orden importa: al revés, el usuario leería "venció, esperate" de un token que
            // en realidad ya está quemado, y no tiene nada que esperar.
            UserEntity alreadyActive = user(3, OTHER_EMAIL, true, RoleType.USER);
            alreadyActive.setActivationExpiresAt(new Timestamp(System.currentTimeMillis() - 1_000));
            when(userRepository.findByActivationToken(anyString()))
                    .thenReturn(Optional.of(alreadyActive));

            assertThrows(ActivationTokenAlreadyUsedException.class,
                    () -> userService.activate(request("raw-token")));
        }
    }

    @Nested
    @DisplayName("findUsers / findActiveUsers")
    class Listings {

        @Test
        @DisplayName("maps every stored user")
        void mapsEveryUser() {
            UserEntity first = user(1, "a@example.com", true, RoleType.USER);
            UserEntity second = user(2, "b@example.com", false, RoleType.ADMIN);
            when(userRepository.findAllByOrderByIdAsc()).thenReturn(List.of(first, second));
            when(userConverter.userToUserResponseDTO(first)).thenReturn(converted);
            when(userConverter.userToUserResponseDTO(second)).thenReturn(converted);

            assertEquals(2, userService.findUsers().size());
        }

        @Test
        @DisplayName("queries only the active flag when listing active users")
        void queriesTheActiveFlag() {
            UserEntity active = user(1, "a@example.com", true, RoleType.USER);
            when(userRepository.findAllByActive(true)).thenReturn(List.of(active));
            when(userConverter.userToUserResponseDTO(active)).thenReturn(converted);

            assertEquals(1, userService.findActiveUsers().size());
            verify(userRepository).findAllByActive(true);
        }
    }

    @Nested
    @DisplayName("findUser")
    class FindUser {

        @Test
        @DisplayName("returns the requested user querying the repository once")
        void queriesTheRepositoryOnce() {
            UserEntity entity = user(7, OTHER_EMAIL, true, RoleType.USER);
            when(userRepository.findById(7)).thenReturn(Optional.of(entity));
            stubConversion(entity);

            assertSame(converted, userService.findUser(7));
            verify(userRepository).findById(7);
        }

        @Test
        @DisplayName("throws when the id does not exist")
        void throwsWhenMissing() {
            when(userRepository.findById(99)).thenReturn(Optional.empty());

            assertThrows(UserNotExistException.class, () -> userService.findUser(99));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deactivates the user instead of removing the row")
        void deactivatesTheUser() {
            UserEntity entity = user(2, OTHER_EMAIL, true, RoleType.USER);
            when(userRepository.findById(2)).thenReturn(Optional.of(entity));
            when(userRepository.save(entity)).thenReturn(entity);
            stubConversion(entity);

            assertSame(converted, userService.delete(2, CALLER_EMAIL));
            assertFalse(entity.isActive(), "delete must be a soft delete");
            verify(userRepository).save(entity);
        }

        @Test
        @DisplayName("rejects an already inactive user")
        void rejectsInactiveUser() {
            when(userRepository.findById(2))
                    .thenReturn(Optional.of(user(2, OTHER_EMAIL, false, RoleType.USER)));

            assertThrows(UserInactiveException.class, () -> userService.delete(2, CALLER_EMAIL));
        }

        @Test
        @DisplayName("rejects deleting an administrator")
        void rejectsAdministrators() {
            when(userRepository.findById(2))
                    .thenReturn(Optional.of(user(2, OTHER_EMAIL, true, RoleType.ADMIN)));

            assertThrows(ForbiddenException.class, () -> userService.delete(2, CALLER_EMAIL));
        }

        @Test
        @DisplayName("rejects self deletion")
        void rejectsSelfDeletion() {
            when(userRepository.findById(2))
                    .thenReturn(Optional.of(user(2, CALLER_EMAIL, true, RoleType.USER)));

            assertThrows(ForbiddenException.class, () -> userService.delete(2, CALLER_EMAIL));
        }

        @Test
        @DisplayName("throws when the id does not exist")
        void throwsWhenMissing() {
            when(userRepository.findById(99)).thenReturn(Optional.empty());

            assertThrows(UserNotExistException.class, () -> userService.delete(99, CALLER_EMAIL));
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("reactivates an inactive user and notifies without touching the password")
        void reactivatesAnInactiveUser() {
            UserEntity entity = user(2, OTHER_EMAIL, false, RoleType.USER);
            when(userRepository.findById(2)).thenReturn(Optional.of(entity));
            when(userRepository.saveAndFlush(entity)).thenReturn(entity);
            stubConversion(entity);

            assertSame(converted, userService.updateStatus(2, true, CALLER_EMAIL));
            assertTrue(entity.isActive(), "an inactive user must be reactivated");
            assertEquals(ENCODED_SECRET, entity.getPassword(),
                    "reactivation must not rotate the password: the user logs in with the one they chose");
            verify(userRepository).saveAndFlush(entity);
            verify(emailService).sendEmail(any(String[].class), anyString(), anyString(), any(Map.class));
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("deactivates an active user")
        void deactivatesAnActiveUser() {
            UserEntity entity = user(2, OTHER_EMAIL, true, RoleType.USER);
            when(userRepository.findById(2)).thenReturn(Optional.of(entity));
            when(userRepository.saveAndFlush(entity)).thenReturn(entity);
            stubConversion(entity);

            assertSame(converted, userService.updateStatus(2, false, CALLER_EMAIL));
            assertFalse(entity.isActive());
            verify(userRepository).saveAndFlush(entity);
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("is a no-op when the state already matches")
        void isIdempotent() {
            UserEntity entity = user(2, OTHER_EMAIL, true, RoleType.USER);
            when(userRepository.findById(2)).thenReturn(Optional.of(entity));
            stubConversion(entity);

            assertSame(converted, userService.updateStatus(2, true, CALLER_EMAIL));
            verify(userRepository, never()).save(any(UserEntity.class));
            verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
            verifyNoInteractions(emailService);
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("rejects changing the caller's own status")
        void rejectsSelfUpdate() {
            when(userRepository.findById(2))
                    .thenReturn(Optional.of(user(2, CALLER_EMAIL, true, RoleType.ADMIN)));

            assertThrows(ForbiddenException.class,
                    () -> userService.updateStatus(2, false, CALLER_EMAIL));
            verifyNoInteractions(loginThrottle);
        }

        @Test
        @DisplayName("protects the last active administrator")
        void protectsTheLastAdmin() {
            UserEntity admin = user(1, OTHER_EMAIL, true, RoleType.ADMIN);
            when(userRepository.findById(1)).thenReturn(Optional.of(admin));
            when(userRepository.findAllByRoleForUpdate(RoleType.ADMIN)).thenReturn(List.of(admin));

            assertThrows(ForbiddenException.class,
                    () -> userService.updateStatus(1, false, CALLER_EMAIL));
            verify(userRepository, never()).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("throws when the id does not exist")
        void throwsWhenMissing() {
            when(userRepository.findById(99)).thenReturn(Optional.empty());

            assertThrows(UserNotExistException.class,
                    () -> userService.updateStatus(99, false, CALLER_EMAIL));
        }

        @Test
        @DisplayName("rejects reactivating an account that never confirmed its registration")
        void rejectsReactivatingAPendingAccount() {
            UserEntity entity = pendingUser(2, OTHER_EMAIL, RoleType.USER);
            when(userRepository.findById(2)).thenReturn(Optional.of(entity));

            assertThrows(PendingActivationException.class,
                    () -> userService.updateStatus(2, true, CALLER_EMAIL));
            verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("rejects suspending a pending account instead of silently no-oping")
        void rejectsSuspendingAPendingAccount() {
            UserEntity entity = pendingUser(2, OTHER_EMAIL, RoleType.USER);
            when(userRepository.findById(2)).thenReturn(Optional.of(entity));

            // Suspender una cuenta ya inactiva igualaría "nunca confirmada" con "suspendida".
            // Por eso el guard va antes del no-op: sin él, esto devolvería un 200 mentiroso.
            assertThrows(PendingActivationException.class,
                    () -> userService.updateStatus(2, false, CALLER_EMAIL));
            verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        }
    }

    @Nested
    @DisplayName("updateRole")
    class UpdateRole {

        @Test
        @DisplayName("replaces the current role")
        void replacesTheRole() {
            UserEntity entity = user(2, OTHER_EMAIL, true, RoleType.USER);
            RoleEntity admin = role(RoleType.ADMIN);
            when(userRepository.findById(2)).thenReturn(Optional.of(entity));
            when(roleService.findRoleByType(RoleType.ADMIN)).thenReturn(admin);
            when(userRepository.save(entity)).thenReturn(entity);
            stubConversion(entity);

            assertSame(converted, userService.updateRole(2, RoleType.ADMIN, CALLER_EMAIL));
            assertEquals(Set.of(admin), entity.getRoleEntities());
        }

        @Test
        @DisplayName("rejects an inactive user")
        void rejectsInactiveUser() {
            when(userRepository.findById(2))
                    .thenReturn(Optional.of(user(2, OTHER_EMAIL, false, RoleType.USER)));

            assertThrows(UserInactiveException.class,
                    () -> userService.updateRole(2, RoleType.ADMIN, CALLER_EMAIL));
        }

        @Test
        @DisplayName("rejects changing the caller's own role")
        void rejectsSelfUpdate() {
            when(userRepository.findById(1))
                    .thenReturn(Optional.of(user(1, CALLER_EMAIL, true, RoleType.ADMIN)));

            assertThrows(ForbiddenException.class,
                    () -> userService.updateRole(1, RoleType.USER, CALLER_EMAIL));
            verifyNoInteractions(roleService);
        }

        @Test
        @DisplayName("protects the last active administrator from demotion")
        void protectsTheLastAdmin() {
            UserEntity admin = user(1, OTHER_EMAIL, true, RoleType.ADMIN);
            when(userRepository.findById(1)).thenReturn(Optional.of(admin));
            when(roleService.findRoleByType(RoleType.USER)).thenReturn(role(RoleType.USER));
            when(userRepository.findAllByRoleForUpdate(RoleType.ADMIN)).thenReturn(List.of(admin));

            assertThrows(ForbiddenException.class,
                    () -> userService.updateRole(1, RoleType.USER, CALLER_EMAIL));
            verify(userRepository, never()).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("throws when the id does not exist")
        void throwsWhenMissing() {
            when(userRepository.findById(99)).thenReturn(Optional.empty());

            assertThrows(UserNotExistException.class,
                    () -> userService.updateRole(99, RoleType.ADMIN, CALLER_EMAIL));
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("updates the profile without touching the password")
        void updatesProfileOnly() {
            UserEntity entity = user(1, CALLER_EMAIL, true, RoleType.USER);
            when(userRepository.findByEmail(CALLER_EMAIL)).thenReturn(Optional.of(entity));
            when(userRepository.save(entity)).thenReturn(entity);
            stubConversion(entity);

            userService.updateUser(
                    new UpdateUserRequestDTO("New", "Name", null, null), CALLER_EMAIL, CLIENT_IP);

            assertEquals("New", entity.getFirstname());
            assertEquals("Name", entity.getLastname());
            assertEquals(ENCODED_SECRET, entity.getPassword(), "the password must be untouched");
            verifyNoInteractions(loginThrottle);
        }

        @Test
        @DisplayName("changes the password when the current one matches")
        void changesThePassword() {
            UserEntity entity = user(1, CALLER_EMAIL, true, RoleType.USER);
            when(userRepository.findByEmail(CALLER_EMAIL)).thenReturn(Optional.of(entity));
            when(loginThrottle.retryAfterSeconds(CALLER_EMAIL, CLIENT_IP)).thenReturn(0L);
            when(passwordEncoder.matches("old-secret", ENCODED_SECRET)).thenReturn(true);
            when(passwordEncoder.encode("new-secret")).thenReturn("new-encoded");
            when(userRepository.save(entity)).thenReturn(entity);
            stubConversion(entity);

            userService.updateUser(
                    new UpdateUserRequestDTO("Test", "User", "new-secret", "old-secret"),
                    CALLER_EMAIL, CLIENT_IP);

            assertEquals("new-encoded", entity.getPassword());
            verify(loginThrottle).recordSuccess(CALLER_EMAIL);
        }

        @Test
        @DisplayName("requires the current password when changing the password")
        void requiresTheCurrentPassword() {
            when(loginThrottle.retryAfterSeconds(CALLER_EMAIL, CLIENT_IP)).thenReturn(0L);

            assertThrows(CurrentPasswordRequiredException.class,
                    () -> userService.updateUser(
                            new UpdateUserRequestDTO("Test", "User", "new-secret", null),
                            CALLER_EMAIL, CLIENT_IP));
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("rejects a wrong current password and counts the failure")
        void rejectsWrongCurrentPassword() {
            UserEntity entity = user(1, CALLER_EMAIL, true, RoleType.USER);
            when(loginThrottle.retryAfterSeconds(CALLER_EMAIL, CLIENT_IP)).thenReturn(0L);
            when(userRepository.findByEmail(CALLER_EMAIL)).thenReturn(Optional.of(entity));
            when(passwordEncoder.matches("wrong", ENCODED_SECRET)).thenReturn(false);

            assertThrows(CurrentPasswordInvalidException.class,
                    () -> userService.updateUser(
                            new UpdateUserRequestDTO("Test", "User", "new-secret", "wrong"),
                            CALLER_EMAIL, CLIENT_IP));
            verify(loginThrottle).recordFailure(CALLER_EMAIL, CLIENT_IP);
            verify(userRepository, never()).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("refuses the change while the caller is throttled")
        void refusesWhileThrottled() {
            when(loginThrottle.retryAfterSeconds(CALLER_EMAIL, CLIENT_IP)).thenReturn(42L);

            assertThrows(TooManyAttemptsException.class,
                    () -> userService.updateUser(
                            new UpdateUserRequestDTO("Test", "User", "new-secret", "old-secret"),
                            CALLER_EMAIL, CLIENT_IP));
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throws when the caller no longer exists")
        void throwsWhenCallerIsGone() {
            when(userRepository.findByEmail(CALLER_EMAIL)).thenReturn(Optional.empty());

            assertThrows(UserNotExistException.class,
                    () -> userService.updateUser(
                            new UpdateUserRequestDTO("New", "Name", null, null),
                            CALLER_EMAIL, CLIENT_IP));
        }
    }
}
