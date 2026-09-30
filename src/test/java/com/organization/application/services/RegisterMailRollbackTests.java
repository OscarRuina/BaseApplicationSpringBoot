package com.organization.application.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.organization.application.AbstractIntegrationTest;
import com.organization.application.configurations.email.service.IEmailService;
import com.organization.application.configurations.exceptions.MailSendException;
import com.organization.application.configurations.exceptions.TooManyAttemptsException;
import com.organization.application.configurations.security.throttle.RegistrationThrottle;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IRoleRepository;
import com.organization.application.repositories.IUserRepository;
import com.organization.application.services.implementations.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RegisterMailRollbackTests extends AbstractIntegrationTest {

    private static final String EMAIL = "rollback@example.com";

    private static final String CLIENT_IP = "203.0.113.55";

    @Autowired
    private UserService userService;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private RegistrationThrottle registrationThrottle;

    @MockBean
    private IEmailService emailService;

    private RegisterUserRequestDTO request() {
        return new RegisterUserRequestDTO("Test", "User", EMAIL);
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        registrationThrottle.clear();
        if (roleRepository.findAll().isEmpty()) {
            roleRepository.save(RoleEntity.builder().type(RoleType.USER).build());
        }
    }

    @Test
    @DisplayName("A failed mail delivery leaves no user persisted")
    void mailFailureRollsBackRegistration() {
        doThrow(new MailSendException("ERROR Sending Mail"))
                .when(emailService).sendEmail(any(), anyString(), anyString(), any());

        assertThrows(MailSendException.class, () -> userService.register(request(), CLIENT_IP));

        assertTrue(userRepository.findByEmail(EMAIL).isEmpty(),
                "the user must not survive a failed mail delivery");
    }

    @Test
    @DisplayName("A successful mail delivery persists the pending user")
    void successfulDeliveryPersistsUser() {
        userService.register(request(), CLIENT_IP);

        UserEntity stored = userRepository.findByEmail(EMAIL).orElseThrow();
        assertTrue(stored.isPendingActivation(),
                "the row is persisted, but it is unusable until the emailed link is followed");
    }

    @Test
    @DisplayName("A throttled client never reaches the repository")
    void throttledRegistrationDoesNotTouchTheDatabase() {
        doThrow(new MailSendException("ERROR Sending Mail"))
                .when(emailService).sendEmail(any(), anyString(), anyString(), any());
        for (int attempt = 0; attempt < 20; attempt++) {
            assertThrows(MailSendException.class, () -> userService.register(request(), CLIENT_IP));
        }

        // Con los 20 intentos consumidos, el 21o no llega ni a tocar la base. El throttle se
        // mira al principio justamente para eso: sin esa fila, cada intento rechazado por
        // correo duplicado seguiría insumiendo presupuesto.
        assertThrows(TooManyAttemptsException.class, () -> userService.register(request(), CLIENT_IP));

        assertTrue(userRepository.findByEmail(EMAIL).isEmpty(),
                "a throttled client cannot create rows");
    }
}
