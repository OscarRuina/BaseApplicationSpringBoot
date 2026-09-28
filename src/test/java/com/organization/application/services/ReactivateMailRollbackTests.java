package com.organization.application.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.organization.application.AbstractIntegrationTest;
import com.organization.application.configurations.email.service.IEmailService;
import com.organization.application.configurations.exceptions.MailSendException;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IRoleRepository;
import com.organization.application.repositories.IUserRepository;
import com.organization.application.services.implementations.UserService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ReactivateMailRollbackTests extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@example.com";

    private static final String TARGET_EMAIL = "reactivate@example.com";

    @Autowired
    private UserService userService;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private IEmailService emailService;

    private UserEntity target;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        if (roleRepository.findAll().isEmpty()) {
            roleRepository.save(RoleEntity.builder().type(RoleType.USER).build());
        }
        target = saveInactiveUser(TARGET_EMAIL);
    }

    @Test
    @DisplayName("A failed mail delivery leaves the user inactive with the old password")
    void mailFailureRollsBackReactivation() {
        doThrow(new MailSendException("ERROR Sending Mail"))
                .when(emailService).sendEmail(any(), anyString(), anyString(), any());

        assertThrows(MailSendException.class,
                () -> userService.updateStatus(target.getId(), true, ADMIN_EMAIL));

        UserEntity reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertFalse(reloaded.isActive(),
                "the user must stay inactive when the mail fails");
        assertTrue(passwordEncoder.matches("original-password", reloaded.getPassword()),
                "the old password must survive the rollback");
    }

    @Test
    @DisplayName("A successful mail delivery reactivates the user and rotates the password")
    void successfulDeliveryReactivatesUser() {
        userService.updateStatus(target.getId(), true, ADMIN_EMAIL);

        UserEntity reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertTrue(reloaded.isActive(), "the user must be active after a successful delivery");
        assertFalse(passwordEncoder.matches("original-password", reloaded.getPassword()),
                "the password must be rotated on reactivation");
    }

    private UserEntity saveInactiveUser(String email) {
        RoleEntity role = roleRepository.findByType(RoleType.USER).orElseThrow();
        return userRepository.save(UserEntity.builder()
                .firstname("Test")
                .lastname("User")
                .email(email)
                .password(passwordEncoder.encode("original-password"))
                .active(false)
                .roleEntities(Set.of(role))
                .build());
    }
}