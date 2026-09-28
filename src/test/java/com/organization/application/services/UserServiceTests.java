package com.organization.application.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.organization.application.configurations.exceptions.CurrentPasswordInvalidException;
import com.organization.application.configurations.exceptions.CurrentPasswordRequiredException;
import com.organization.application.configurations.exceptions.ForbiddenException;
import com.organization.application.configurations.exceptions.InvalidRoleException;
import com.organization.application.configurations.exceptions.TooManyAttemptsException;
import com.organization.application.configurations.exceptions.UserAlreadyExistException;
import com.organization.application.configurations.exceptions.UserInactiveException;
import com.organization.application.configurations.exceptions.UserNotExistException;
import com.organization.application.configurations.security.throttle.LoginThrottle;
import com.organization.application.converters.UserConverter;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.request.UpdateUserRequestDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IUserRepository;
import com.organization.application.services.implementations.UserService;
import com.organization.application.services.interfaces.IRoleService;
import java.util.HashSet;
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
import org.mockito.InjectMocks;
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

    @InjectMocks
    private UserService userService;

    private UserResponseDTO converted;

    @BeforeEach
    void setUp() {
        converted = mock(UserResponseDTO.class);
    }

    private static RoleEntity role(RoleType type) {
        return RoleEntity.builder().type(type).build();
    }

    private static UserEntity user(Integer id, String email, boolean active, RoleType... types) {
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

        @Test
        @DisplayName("persists an active user with an encoded temporary password")
        void persistsAnActiveUser() {
            when(userRepository.existsByEmail(OTHER_EMAIL)).thenReturn(false);
            when(roleService.findRoleByType(RoleType.USER)).thenReturn(role(RoleType.USER));
            when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_SECRET);
            when(userRepository.saveAndFlush(any(UserEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            userService.register(new RegisterUserRequestDTO("Test", "User", OTHER_EMAIL, RoleType.USER));

            var captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).saveAndFlush(captor.capture());
            UserEntity saved = captor.getValue();
            assertTrue(saved.isActive(), "a new user must be active");
            assertEquals(ENCODED_SECRET, saved.getPassword(), "the password must be stored encoded");
            assertEquals(OTHER_EMAIL, saved.getEmail());
        }

        @Test
        @DisplayName("rejects an email that is already taken")
        void rejectsDuplicatedEmail() {
            when(userRepository.existsByEmail(OTHER_EMAIL)).thenReturn(true);

            assertThrows(UserAlreadyExistException.class,
                    () -> userService.register(
                            new RegisterUserRequestDTO("Test", "User", OTHER_EMAIL, RoleType.USER)));
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("rejects any role other than USER")
        void rejectsPrivilegedRegistration() {
            when(userRepository.existsByEmail(OTHER_EMAIL)).thenReturn(false);

            assertThrows(InvalidRoleException.class,
                    () -> userService.register(
                            new RegisterUserRequestDTO("Test", "User", OTHER_EMAIL, RoleType.ADMIN)));
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("translates a concurrent insert into a conflict")
        void translatesConcurrentInsert() {
            when(userRepository.existsByEmail(OTHER_EMAIL)).thenReturn(false);
            when(roleService.findRoleByType(RoleType.USER)).thenReturn(role(RoleType.USER));
            when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_SECRET);
            doThrow(new DataIntegrityViolationException("duplicate key"))
                    .when(userRepository).saveAndFlush(any(UserEntity.class));

            assertThrows(UserAlreadyExistException.class,
                    () -> userService.register(
                            new RegisterUserRequestDTO("Test", "User", OTHER_EMAIL, RoleType.USER)));
        }

        @Test
        @DisplayName("sends the welcome mail to the new user")
        void sendsTheWelcomeMail() {
            when(userRepository.existsByEmail(OTHER_EMAIL)).thenReturn(false);
            when(roleService.findRoleByType(RoleType.USER)).thenReturn(role(RoleType.USER));
            when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_SECRET);
            when(userRepository.saveAndFlush(any(UserEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            userService.register(new RegisterUserRequestDTO("Test", "User", OTHER_EMAIL, RoleType.USER));

            verify(emailService).sendEmail(any(String[].class), anyString(), anyString(), any(Map.class));
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
        @DisplayName("reactivates an inactive user with fresh credentials sent by mail")
        void reactivatesAnInactiveUser() {
            UserEntity entity = user(2, OTHER_EMAIL, false, RoleType.USER);
            when(userRepository.findById(2)).thenReturn(Optional.of(entity));
            when(userRepository.saveAndFlush(entity)).thenReturn(entity);
            when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_SECRET);
            stubConversion(entity);

            assertSame(converted, userService.updateStatus(2, true, CALLER_EMAIL));
            assertTrue(entity.isActive(), "an inactive user must be reactivated");
            assertEquals(ENCODED_SECRET, entity.getPassword(),
                    "the password must be rotated on reactivation");
            verify(passwordEncoder).encode(anyString());
            verify(userRepository).saveAndFlush(entity);
            verify(emailService).sendEmail(any(String[].class), anyString(), anyString(), any(Map.class));
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
