package com.organization.application.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.organization.application.AbstractSecuredIntegrationTest;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.services.interfaces.IUserService;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("UserService fetch plans")
class UserServiceQueryTests extends AbstractSecuredIntegrationTest {

    private static final String MULTI_ROLE_EMAIL = "multi@example.com";

    @Autowired
    private IUserService userService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UserEntity multiRoleUser;

    @BeforeEach
    void seedMultiRoleUser() {
        multiRoleUser = persistWithRoles(MULTI_ROLE_EMAIL, true, RoleType.USER, RoleType.ADMIN);
        statistics().clear();
    }

    @Test
    @DisplayName("findUsers() resolves every user and every role in one query")
    void findUsersResolvesInASingleQuery() {
        List<UserResponseDTO> users = userService.findUsers();

        assertEquals(4, users.size(), "one entry per user, so the role join must not duplicate rows");
        assertEquals(Set.of("USER", "ADMIN"), rolesOf(users, MULTI_ROLE_EMAIL),
                "a user with two roles must come back with both of them");
        assertEquals(1, statements(), "findUsers() must not issue one extra select per user");
    }

    @Test
    @DisplayName("findActiveUsers() resolves every user and every role in one query")
    void findActiveUsersResolvesInASingleQuery() {
        List<UserResponseDTO> users = userService.findActiveUsers();

        assertEquals(3, users.size(), "the inactive seeded user must stay out of the listing");
        assertEquals(Set.of("USER", "ADMIN"), rolesOf(users, MULTI_ROLE_EMAIL));
        assertEquals(1, statements(), "findActiveUsers() must not issue one extra select per user");
    }

    @Test
    @DisplayName("findUser() resolves the user and its roles in one query")
    void findUserResolvesInASingleQuery() {
        userService.findUser(multiRoleUser.getId());

        assertEquals(1, statements(), "findUser() must not issue a second select for the roles");
    }

    @Test
    @DisplayName("me() resolves the caller and its roles in one query")
    void meResolvesInASingleQuery() {
        userService.me(MULTI_ROLE_EMAIL);

        assertEquals(1, statements(), "me() must not issue a second select for the roles");
    }

    @Test
    @DisplayName("existsByEmail() answers existence with a single lightweight probe")
    void existsByEmailStaysALightweightProbe() {
        assertTrue(userRepository.existsByEmail(ADMIN_EMAIL));
        assertFalse(userRepository.existsByEmail("absent@example.com"));
        assertEquals(2, statements(), "each existence probe must cost exactly one statement");
    }

    private Set<String> rolesOf(List<UserResponseDTO> users, String email) {
        return users.stream()
                .filter(user -> user.getEmail().equals(email))
                .flatMap(user -> user.getRoles().stream())
                .map(role -> role.getRole())
                .collect(Collectors.toSet());
    }

    private UserEntity persistWithRoles(String email, boolean active, RoleType... types) {
        Set<RoleEntity> roles = new HashSet<>();
        for (RoleType type : types) {
            roles.add(roleRepository.findByType(type).orElseThrow());
        }
        return userRepository.saveAndFlush(UserEntity.builder()
                .firstname("Test")
                .lastname("Multi")
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .active(active)
                .roleEntities(roles)
                .build());
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private long statements() {
        return statistics().getPrepareStatementCount();
    }
}
