package com.organization.application.services.interfaces;

import com.organization.application.dtos.request.LoginRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;

public interface IAuthService {

    LoginResponseDTO login(LoginRequestDTO loginRequestDTO, BindingResult bindingResult);
}
