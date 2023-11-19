package com.organization.application.services.implementations;

import com.organization.application.configurations.exceptions.AttributeErrorsException;
import com.organization.application.configurations.exceptions.AuthenticationException;
import com.organization.application.configurations.security.jwt.JwtUtil;
import com.organization.application.converters.UserConverter;
import com.organization.application.dtos.request.LoginRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.messages.ExceptionMessages;
import com.organization.application.services.interfaces.IAuthService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

@Service
@Slf4j
public class AuthService implements IAuthService {

    private final AuthenticationManager authenticationManager;

    private final UserDetailsServiceImpl userDetailsService;

    private final JwtUtil jwtUtil;

    private final UserConverter userConverter;

    public AuthService(AuthenticationManager authenticationManager,
            UserDetailsServiceImpl userDetailsService, JwtUtil jwtUtil, UserConverter userConverter) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.userConverter = userConverter;
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO loginRequestDTO, BindingResult bindingResult) {
        log.info("Inside login");
        if (bindingResult.hasErrors()){
            throw new AttributeErrorsException(ExceptionMessages.INVALID_ATTRIBUTES);
        }
        try{
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequestDTO.getUsername(),loginRequestDTO.getPassword())
            );

            if (authentication.isAuthenticated()){
                if (userDetailsService.getUser().isActive()){
                    return userConverter.userToLoginResponseDTO(userDetailsService.getUser(),
                            jwtUtil.createToken(userDetailsService.getUser().getEmail(),
                                    userDetailsService.getUser().getRoleEntities().stream().map(
                                            roleEntity -> roleEntity.getType().name()
                                    ).collect(Collectors.toSet())));

                }else {
                    log.error(ExceptionMessages.USER_NOT_ACTIVE);
                    throw new AuthenticationException(ExceptionMessages.USER_NOT_ACTIVE);
                }
            }
        }catch (Exception e){
            log.error("{}", e.getMessage());
        }
        throw new AuthenticationException(ExceptionMessages.BAD_CREDENTIALS);

    }

}
