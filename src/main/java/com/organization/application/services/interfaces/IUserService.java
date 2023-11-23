package com.organization.application.services.interfaces;

import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.request.UpdateUserRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.validation.BindingResult;

public interface IUserService {

    UserResponseDTO register(RegisterUserRequestDTO registerUserRequestDTO, BindingResult bindingResult);

    LoginResponseDTO me(HttpServletRequest request);

    List<UserResponseDTO> findUsers();

    List<UserResponseDTO> findUsersActive(boolean active);

    UserResponseDTO findUser(Integer id);

    UserResponseDTO delete(Integer id, HttpServletRequest request);

    UserResponseDTO updateStatus(Integer id, HttpServletRequest request);

    UserResponseDTO updateRole(Integer id, String role);

    UserResponseDTO updateUser(UpdateUserRequestDTO updateUserRequestDTO, BindingResult bindingResult
            , HttpServletRequest request);

}
