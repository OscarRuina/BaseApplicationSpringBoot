package com.organization.application.services.implementations;

import com.organization.application.configurations.security.jwt.JwtUtil;
import com.organization.application.converters.UserConverter;
import com.organization.application.dtos.request.LoginRequestDTO;
import com.organization.application.services.interfaces.IAuthService;
import com.organization.application.utils.ApplicationResponse;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuthService implements IAuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserConverter userConverter;

    @Override
    public ResponseEntity<Object> login(LoginRequestDTO loginRequestDTO) {
        log.info("Inside login");
        try{
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequestDTO.getUsername(),loginRequestDTO.getPassword())
            );

            if (authentication.isAuthenticated()){
                if (userDetailsService.getUser().isActive()){
                    return ApplicationResponse.getResponseEntity(
                            userConverter.userToLoginResponseDTO(userDetailsService.getUser(),
                                    jwtUtil.createToken(userDetailsService.getUser().getEmail(),
                                            userDetailsService.getUser().getRoleEntities().stream().map(
                                                    roleEntity -> roleEntity.getType().name()
                                            ).collect(Collectors.toSet())))
                            , HttpStatus.OK
                    );

                }else {
                    log.error("Not Authenticated");
                    return ApplicationResponse.getResponseEntity("ERROR user is not active", HttpStatus.BAD_REQUEST);
                }
            }
        }catch (Exception e){
            log.error("{}", e.getMessage());
        }
        return ApplicationResponse.getResponseEntity("ERROR Bad Credentials", HttpStatus.BAD_REQUEST);
    }

}
