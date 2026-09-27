package com.organization.application.services.implementations;

import com.organization.application.configurations.exceptions.AuthenticationException;
import com.organization.application.configurations.exceptions.AuthenticationServiceUnavailableException;
import com.organization.application.configurations.exceptions.TooManyAttemptsException;
import com.organization.application.configurations.security.jwt.JwtUtil;
import com.organization.application.configurations.security.service.UserPrincipal;
import com.organization.application.configurations.security.throttle.LoginThrottle;
import com.organization.application.converters.UserConverter;
import com.organization.application.dtos.request.LoginRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.messages.ExceptionMessages;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.services.interfaces.IAuthService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuthService implements IAuthService {

    private final AuthenticationManager authenticationManager;

    private final JwtUtil jwtUtil;

    private final UserConverter userConverter;

    private final LoginThrottle loginThrottle;

    public AuthService(AuthenticationManager authenticationManager,
            JwtUtil jwtUtil, UserConverter userConverter, LoginThrottle loginThrottle) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userConverter = userConverter;
        this.loginThrottle = loginThrottle;
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO loginRequestDTO, String clientIp) {
        long retryAfter = loginThrottle.retryAfterSeconds(loginRequestDTO.getUsername(), clientIp);
        if (retryAfter > 0) {
            throw new TooManyAttemptsException(ExceptionMessages.TOO_MANY_ATTEMPTS, retryAfter);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequestDTO.getUsername(),
                            loginRequestDTO.getPassword())
            );
        }catch (AuthenticationServiceException e){
            log.error("Authentication infrastructure failure. Username: {}",
                    loginRequestDTO.getUsername(), e);
            throw new AuthenticationServiceUnavailableException(
                    ExceptionMessages.AUTH_SERVICE_UNAVAILABLE);
        }catch (DisabledException e){
            log.info("Login rejected: account is not active. Username: {}",
                    loginRequestDTO.getUsername());
            loginThrottle.recordFailure(loginRequestDTO.getUsername(), clientIp);
            throw new AuthenticationException(ExceptionMessages.BAD_CREDENTIALS);
        }catch (org.springframework.security.core.AuthenticationException e){
            log.debug("Login failed [{}]. Reason: {}", loginRequestDTO.getUsername(),
                    e.getClass().getSimpleName());
            loginThrottle.recordFailure(loginRequestDTO.getUsername(), clientIp);
            throw new AuthenticationException(ExceptionMessages.BAD_CREDENTIALS);
        }

        loginThrottle.recordSuccess(loginRequestDTO.getUsername());

        UserEntity user = ((UserPrincipal) authentication.getPrincipal()).getEntity();

        String token = jwtUtil.createToken(user.getEmail(),
                user.getRoleEntities().stream()
                        .map(roleEntity -> roleEntity.getType().name())
                        .collect(Collectors.toSet()));

        return userConverter.userToLoginResponseDTO(user, token);
    }
}
