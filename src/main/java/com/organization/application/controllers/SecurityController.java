package com.organization.application.controllers;

import com.organization.application.configurations.exceptions.AttributeErrorsException;
import com.organization.application.configurations.exceptions.AuthenticationException;
import com.organization.application.dtos.request.LoginRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.messages.ResponseMessages;
import com.organization.application.services.implementations.AuthService;
import com.organization.application.dtos.response.ApplicationResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
public class SecurityController {

    private final AuthService authService;

    public SecurityController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApplicationResponse<LoginResponseDTO>> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO,
            BindingResult bindingResult){
        log.info("POST:api/auth/login");
        try{
            LoginResponseDTO dto=  authService.login(loginRequestDTO,bindingResult);
            log.info(ResponseMessages.LOGIN_SUCCESSFUL);
            return new ResponseEntity<>(new ApplicationResponse<>(dto,ResponseMessages.LOGIN_SUCCESSFUL),HttpStatus.OK);
        }catch (AuthenticationException | AttributeErrorsException e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

}
