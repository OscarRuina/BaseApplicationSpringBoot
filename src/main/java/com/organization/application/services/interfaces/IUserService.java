package com.organization.application.services.interfaces;

import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

public interface IUserService {

    UserResponseDTO register(RegisterUserRequestDTO registerUserRequestDTO);

    ResponseEntity<Object> me(HttpServletRequest request);

    ResponseEntity<Object> findUsers();

    ResponseEntity<Object> findUsersActive(boolean active);

    ResponseEntity<Object> findUser(Integer id);

    ResponseEntity<Object> delete(Integer id, HttpServletRequest request);

    ResponseEntity<Object> updateStatus(Integer id, HttpServletRequest request);

    ResponseEntity<Object> updateRole(Integer id, String role);

}
