package com.organization.application.services.interfaces;

import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.request.UpdateUserRequestDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.models.enums.RoleType;
import java.util.List;

public interface IUserService {

    UserResponseDTO register(RegisterUserRequestDTO registerUserRequestDTO);

    UserResponseDTO me(String callerEmail);

    List<UserResponseDTO> findUsers();

    List<UserResponseDTO> findActiveUsers();

    UserResponseDTO findUser(Integer id);

    UserResponseDTO delete(Integer id, String callerEmail);

    UserResponseDTO updateStatus(Integer id, boolean active, String callerEmail);

    UserResponseDTO updateRole(Integer id, RoleType role, String callerEmail);

    UserResponseDTO updateUser(UpdateUserRequestDTO updateUserRequestDTO, String callerEmail,
            String clientIp);
}
