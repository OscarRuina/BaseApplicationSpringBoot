package com.organization.application.controllers;

import com.organization.application.configurations.exceptions.AuthenticationException;
import com.organization.application.dtos.request.LoginRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.services.implementations.AuthService;
import com.organization.application.utils.AppResponse;
import com.organization.application.utils.ApplicationResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
public class SecurityController {

    @Autowired
    private AuthService authService;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AppResponse<LoginResponseDTO>> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO){
        try{
            LoginResponseDTO dto=  authService.login(loginRequestDTO);
            return new ResponseEntity<>(new AppResponse<>(dto,"Login Successful"),HttpStatus.OK);
        }catch (AuthenticationException e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, "An error Occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

}
