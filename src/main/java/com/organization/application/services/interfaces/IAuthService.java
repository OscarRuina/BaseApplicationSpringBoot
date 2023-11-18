package com.organization.application.services.interfaces;

import com.organization.application.dtos.request.LoginRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import org.springframework.http.ResponseEntity;

public interface IAuthService {

    LoginResponseDTO login(LoginRequestDTO loginRequestDTO);


}
