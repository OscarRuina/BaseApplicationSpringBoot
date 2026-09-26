package com.organization.application.services.interfaces;

import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.request.UpdateUserRequestDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import java.util.List;

public interface IUserService {

    UserResponseDTO register(RegisterUserRequestDTO registerUserRequestDTO);

    UserResponseDTO me(String callerEmail);

    List<UserResponseDTO> findUsers();

    List<UserResponseDTO> findUsersActive(boolean active);

    UserResponseDTO findUser(Integer id);

    UserResponseDTO delete(Integer id, String callerEmail);

    UserResponseDTO updateStatus(Integer id, String callerEmail);

    UserResponseDTO updateRole(Integer id, String role);

    UserResponseDTO updateUser(UpdateUserRequestDTO updateUserRequestDTO, String callerEmail);
}
