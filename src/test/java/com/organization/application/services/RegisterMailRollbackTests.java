package com.organization.application.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.organization.application.AbstractIntegrationTest;
import com.organization.application.configurations.email.service.IEmailService;
import com.organization.application.configurations.exceptions.MailSendException;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.models.entities.RoleEntity;
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

    @Autowired
    private UserService userService;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @MockBean
    private IEmailService emailService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        if (roleRepository.findAll().isEmpty()) {
            roleRepository.save(RoleEntity.builder().type(RoleType.USER).build());
        }
    }

    @Test
    @DisplayName("A failed mail delivery leaves no user persisted")
    void mailFailureRollsBackRegistration() {
        doThrow(new MailSendException("ERROR Sending Mail"))
                .when(emailService).sendEmail(any(), anyString(), anyString(), any());

        RegisterUserRequestDTO request =
                new RegisterUserRequestDTO("Test", "User", EMAIL, RoleType.USER);

        assertThrows(MailSendException.class, () -> userService.register(request));

        assertTrue(userRepository.findByEmail(EMAIL).isEmpty(),
                "the user must not survive a failed mail delivery");
    }

    @Test
    @DisplayName("A successful mail delivery persists the user")
    void successfulDeliveryPersistsUser() {
        RegisterUserRequestDTO request =
                new RegisterUserRequestDTO("Test", "User", EMAIL, RoleType.USER);

        userService.register(request);

        assertTrue(userRepository.findByEmail(EMAIL).isPresent(),
                "the user must be persisted when the mail is delivered");
    }
}
