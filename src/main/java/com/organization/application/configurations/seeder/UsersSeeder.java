package com.organization.application.configurations.seeder;

import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IRoleRepository;
import com.organization.application.repositories.IUserRepository;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UsersSeeder implements CommandLineRunner {

    private static final String passwordGeneric = "foo1234";

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

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
        userRepository.save(buildUserUser("Consulta","Consulta","consulta@hotmail.com",passwordGeneric));
    }

    private UserEntity buildUserUser(String firstName, String lastName, String email, String password) {
        return UserEntity.builder()
                .firstname(firstName)
                .lastname(lastName)
                .email(email)
                .active(true)
                .password(encryptPassword(password))
                .roleEntities(Set.of(roleRepository.findByType(RoleType.USER).get()))
                .build();
    }

    private void loadUserAdmin() {
        userRepository.save(buildUserAdmin("Oscar","Ruina","oscarruina@hotmail.com",passwordGeneric));
    }

    private UserEntity buildUserAdmin(String firstName, String lastName, String email, String password) {
        return UserEntity.builder()
                .firstname(firstName)
                .lastname(lastName)
                .email(email)
                .active(true)
                .password(encryptPassword(password))
                .roleEntities(Set.of(roleRepository.findByType(RoleType.ADMIN).get()))
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

    private String encryptPassword(String password) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(7);
        return passwordEncoder.encode(password);
    }
}
