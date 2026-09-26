package com.organization.application.configurations.seeder;

import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IRoleRepository;
import com.organization.application.repositories.IUserRepository;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class UsersSeeder implements CommandLineRunner {

    private final IUserRepository userRepository;

    private final IRoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    private final String seedPassword;

    public UsersSeeder(IUserRepository userRepository, IRoleRepository roleRepository,
            PasswordEncoder passwordEncoder, @Value("${app.seed.password}") String seedPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(String... args) throws Exception {
        loadRoles();
        loadUsers();
    }

    private void loadUsers() {
        if (userRepository.count() == 0){
            loadUserAdmin();
            loadUser();
        }
    }

    private void loadUser() {
        userRepository.save(buildUserUser("user","user","user@hotmail.com",seedPassword));
    }

    private UserEntity buildUserUser(String firstName, String lastName, String email, String password) {
        return UserEntity.builder()
                .firstname(firstName)
                .lastname(lastName)
                .email(email)
                .active(true)
                .password(passwordEncoder.encode(password))
                .roleEntities(Set.of(roleRepository.findByType(RoleType.USER).orElseThrow()))
                .build();
    }

    private void loadUserAdmin() {
        userRepository.save(buildUserAdmin("admin","admin","admin@gmail.com",seedPassword));
    }

    private UserEntity buildUserAdmin(String firstName, String lastName, String email, String password) {
        return UserEntity.builder()
                .firstname(firstName)
                .lastname(lastName)
                .email(email)
                .active(true)
                .password(passwordEncoder.encode(password))
                .roleEntities(Set.of(roleRepository.findByType(RoleType.ADMIN).orElseThrow()))
                .build();
    }

    private void loadRoles() {
        if (roleRepository.count() == 0){
            roleRepository.save(buildRole(RoleType.USER));
            roleRepository.save(buildRole(RoleType.ADMIN));
        }
    }

    private RoleEntity buildRole(RoleType roleType) {
        return RoleEntity.builder()
                .type(roleType)
                .build();
    }
}
