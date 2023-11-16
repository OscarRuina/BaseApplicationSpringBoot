package com.organization.application.services.interfaces;

import com.organization.application.dtos.request.LoginRequestDTO;
import org.springframework.http.ResponseEntity;

public interface IAuthService {

    ResponseEntity<Object> login(LoginRequestDTO loginRequestDTO);


}
