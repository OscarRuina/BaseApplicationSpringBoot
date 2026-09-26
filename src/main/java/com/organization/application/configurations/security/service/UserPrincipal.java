package com.organization.application.configurations.security.service;

import com.organization.application.models.entities.UserEntity;
import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails {

    private final UserEntity entity;

    public UserPrincipal(UserEntity entity) {
        this.entity = entity;
    }

    public UserEntity getEntity() {
        return entity;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return entity.getRoleEntities().stream()
                .map(roleEntity -> new SimpleGrantedAuthority(roleEntity.getType().getPrefixedName()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return entity.getPassword();
    }

    @Override
    public String getUsername() {
        return entity.getEmail();
    }
}
