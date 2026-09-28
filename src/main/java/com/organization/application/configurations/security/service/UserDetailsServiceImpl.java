package com.organization.application.configurations.security.service;

import com.organization.application.messages.ExceptionMessages;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.repositories.IUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final IUserRepository userRepository;

    public UserDetailsServiceImpl(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(username).orElseThrow(
                () -> new UsernameNotFoundException(ExceptionMessages.USER_NOT_EXIST)
        );
        return new UserPrincipal(user);
    }
}
