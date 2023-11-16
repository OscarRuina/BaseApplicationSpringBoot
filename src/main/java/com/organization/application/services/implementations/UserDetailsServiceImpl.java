package com.organization.application.services.implementations;

import com.organization.application.models.entities.UserEntity;
import com.organization.application.repositories.IUserRepository;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private IUserRepository userRepository;

    private UserEntity user;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Inside loadUserByUsername {} " , username);
        user = userRepository.findByEmail(username).orElseThrow(
                () -> { throw new UsernameNotFoundException("ERROR username not found: " + username);}
        );
        return User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(
                        user.getRoleEntities().stream().map(roleEntity ->
                            new SimpleGrantedAuthority("ROLE_" + roleEntity.getType().name())
                        ).collect(Collectors.toSet()))
                .build();
    }

    public UserEntity getUser(){
        return user;
    }

}
