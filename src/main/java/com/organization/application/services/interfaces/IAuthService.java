package com.organization.application.services.interfaces;

import com.organization.application.dtos.request.LoginRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;

public interface IAuthService {

    LoginResponseDTO login(LoginRequestDTO loginRequestDTO);
}
