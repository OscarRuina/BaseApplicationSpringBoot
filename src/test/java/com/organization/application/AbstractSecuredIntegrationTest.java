package com.organization.application;

import com.organization.application.configurations.email.service.IEmailService;
import com.organization.application.configurations.security.jwt.JwtUtil;
import com.organization.application.configurations.security.throttle.LoginThrottle;
import com.organization.application.configurations.security.throttle.RegistrationThrottle;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IRoleRepository;
import com.organization.application.repositories.IUserRepository;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractSecuredIntegrationTest extends AbstractIntegrationTest {

    public static final String ADMIN_EMAIL = "admin@example.com";
    public static final String USER_EMAIL = "user@example.com";
    public static final String INACTIVE_EMAIL = "inactive@example.com";
    public static final String ABSENT_EMAIL = "absent@example.com";
    public static final String PASSWORD = "ValidPassw0rd!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected IUserRepository userRepository;

    @Autowired
    protected IRoleRepository roleRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtUtil jwtUtil;

    @MockBean
    protected IEmailService emailService;

    @MockBean
    protected LoginThrottle loginThrottle;

    @Autowired
    protected RegistrationThrottle registrationThrottle;

    protected UserEntity admin;

    protected UserEntity regularUser;

    protected UserEntity inactiveUser;

    @BeforeEach
    void seedDatabase() {
        userRepository.deleteAll();
        seedRoles();
        // El throttle de registro vive en memoria del proceso, así que sobrevive al borrado de
        // la base. Sin este clear un test que agota el presupuesto deja a los demás en 429.
        registrationThrottle.clear();

        admin = persist(ADMIN_EMAIL, true, RoleType.ADMIN);
        regularUser = persist(USER_EMAIL, true, RoleType.USER);
        inactiveUser = persist(INACTIVE_EMAIL, false, RoleType.USER);
    }

    private void seedRoles() {
        for (RoleType type : RoleType.values()) {
            if (roleRepository.findByType(type).isEmpty()) {
                roleRepository.save(RoleEntity.builder().type(type).build());
            }
        }
    }

    private UserEntity persist(String email, boolean active, RoleType type) {
        Set<RoleEntity> roles = new HashSet<>();
        roles.add(roleRepository.findByType(type).orElseThrow());
        return userRepository.save(UserEntity.builder()
                .firstname("Test")
                .lastname(type.name())
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .active(active)
                .activatedAt(new Timestamp(System.currentTimeMillis()))
                .roleEntities(roles)
                .build());
    }

    protected String targetId() {
        return String.valueOf(regularUser.getId());
    }

    protected String bearerFor(UserEntity user) {
        Set<String> roles = user.getRoleEntities().stream()
                .map(roleEntity -> roleEntity.getType().name())
                .collect(Collectors.toSet());
        return "Bearer " + jwtUtil.createToken(user.getEmail(), roles);
    }

    protected String adminToken() {
        return bearerFor(admin);
    }

    protected String userToken() {
        return bearerFor(regularUser);
    }

    protected String inactiveToken() {
        return bearerFor(inactiveUser);
    }
}
