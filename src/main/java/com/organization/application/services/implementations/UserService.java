package com.organization.application.services.implementations;

import com.organization.application.configurations.email.service.IEmailService;
import com.organization.application.configurations.exceptions.AttributeErrorsException;
import com.organization.application.configurations.exceptions.AuthenticationException;
import com.organization.application.configurations.exceptions.UserAlreadyExistException;
import com.organization.application.configurations.exceptions.UserNotExistException;
import com.organization.application.configurations.security.jwt.JwtUtil;
import com.organization.application.configurations.security.service.UserDetailsServiceImpl;
import com.organization.application.converters.UserConverter;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.request.UpdateUserRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.messages.ExceptionMessages;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IUserRepository;
import com.organization.application.services.interfaces.IRoleService;
import com.organization.application.services.interfaces.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

@Service
@Slf4j
public class UserService implements IUserService {

    private final IUserRepository userRepository;

    private final UserConverter userConverter;

    private final UserDetailsServiceImpl userDetailsService;

    private final IRoleService roleService;

    private final JwtUtil jwtUtil;

    private final IEmailService emailService;

    private static final  String BEARER_PART = "Bearer ";

    private static final  String EMAIL_SUBJECT = "Registro de Usuario ";

    public UserService(IUserRepository userRepository, UserConverter userConverter,
            UserDetailsServiceImpl userDetailsService, IRoleService roleService, JwtUtil jwtUtil,
            IEmailService emailService) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
        this.userDetailsService = userDetailsService;
        this.roleService = roleService;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    /**
     * Método encargado de retornar los datos del usuario autenticado en la aplicación
     * @param request
     * @return LoginResponseDTO
     */
    @Override
    public LoginResponseDTO me(HttpServletRequest request) {
        log.info("Inside user service method me ");

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = "";

        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PART)){
            token = authorizationHeader.substring(7);

            return userConverter.userToLoginResponseDTO(userDetailsService.getUser(), token);
        }
        throw new AuthenticationException(ExceptionMessages.BAD_CREDENTIALS);
    }

    /**
     * Método encargado de crear un nuevo usuario en la aplicación
     * @param registerUserRequestDTO
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO register(RegisterUserRequestDTO registerUserRequestDTO, BindingResult bindingResult) {
        log.info("Inside user service method register");
        if (bindingResult.hasErrors()){
            throw new AttributeErrorsException(ExceptionMessages.INVALID_ATTRIBUTES);
        }
        if (userRepository.findByEmail(registerUserRequestDTO.getEmail()).isPresent()){
            throw new UserAlreadyExistException(ExceptionMessages.USER_ALREADY_EXIST);
        }else {
            if (!registerUserRequestDTO.getRole().equalsIgnoreCase(RoleType.USER.name())){
                throw new AuthenticationException(ExceptionMessages.ROLE_NOT_VALID);
            }
            RoleEntity role = roleService.findRoleByType(RoleType.valueOf(registerUserRequestDTO.getRole()));
            log.info(role.getType().name());
            if (registerUserRequestDTO.getRole().equalsIgnoreCase(RoleType.USER.name())){
                UserResponseDTO dto =  userConverter.userToUserResponseDTO(
                        userRepository.save(
                                UserEntity.builder()
                                        .firstname(registerUserRequestDTO.getFirstname())
                                        .lastname(registerUserRequestDTO.getLastname())
                                        .email(registerUserRequestDTO.getEmail())
                                        .password(encryptPassword(registerUserRequestDTO.getPassword()))
                                        .active(true)
                                        .roleEntities(Set.of(role))
                                        .build()
                        )
                );
                String[] toUser = {registerUserRequestDTO.getEmail()};
                String message = "Su usuario es: " + registerUserRequestDTO.getEmail() + " y su contraseña es: "
                        + registerUserRequestDTO.getPassword();
                emailService.sendEmail(toUser, EMAIL_SUBJECT, message);
                return dto;
            }else {
                log.error(ExceptionMessages.CANT_CREATE_USER);
                throw new AuthenticationException(ExceptionMessages.CANT_CREATE_USER);
            }
        }
    }

    /**
     * Método encargado de buscar todos los usuarios de la aplicación
     * @return List<UserResponseDTO>
     */
    @Override
    public List<UserResponseDTO> findUsers() {
        log.info("Inside user service method find users");
        return userRepository.findAll().stream()
                .map(userConverter::userToUserResponseDTO)
                .toList();
    }

    /**
     * Método encargado de buscar todos los usuarios que no han sido eliminados de la aplicación
     * @param active
     * @return List<UserResponseDTO>
     */
    @Override
    public List<UserResponseDTO> findUsersActive(boolean active) {
        log.info("Inside user service method find active users");
        return userRepository.findAllByActive(active).stream()
                .map(userConverter::userToUserResponseDTO)
                .toList();
    }

    /**
     * Método encargado de buscar un usuario en la aplicación
     * @param id
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO findUser(Integer id) {
        log.info("Inside user service method find user by id");
        if (userRepository.findById(id).isEmpty()){
            throw new UserNotExistException(ExceptionMessages.USER_NOT_EXIST);
        }else {
            return userConverter.userToUserResponseDTO(
                    userRepository.findById(id).get()
            );
        }
    }

    /**
     * Método encargado de eliminar un usuario en la aplicación
     * @param id
     * @param request
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO delete(Integer id, HttpServletRequest request) {
        log.info("Inside user service method delete user by id");
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = "";

        if (userRepository.findById(id).isEmpty() || !userRepository.findById(id).get().isActive()){
            throw new UserNotExistException(ExceptionMessages.USER_NOT_EXIST);
        }else{
            if (userRepository.findById(id).get().getRoleEntities().stream().anyMatch(
                    roleEntity -> roleEntity.getType().name().equalsIgnoreCase(RoleType.ADMIN.name()))){
                throw new AuthenticationException(ExceptionMessages.CANT_DELETE);
            }
            if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PART)){
                token = authorizationHeader.substring(7);
                String username = jwtUtil.getUsername(token);
                if (username.equalsIgnoreCase(userRepository.findById(id).get().getEmail())){
                    throw new AuthenticationException(ExceptionMessages.CANT_DELETE);
                }else {
                    UserEntity user = userRepository.findById(id).get();
                    user.setActive(false);
                    userRepository.save(user);
                    return userConverter.userToUserResponseDTO(user);
                }
            }
        }
        throw new AuthenticationException(ExceptionMessages.BAD_CREDENTIALS);
    }

    /**
     * Método encargado de actualizar el estado de un usuario en la aplicación
     * @param id
     * @param request
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO updateStatus(Integer id, HttpServletRequest request) {
        log.info("Inside user service method update status user by id");
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = "";

        if (userRepository.findById(id).isEmpty() || userRepository.findById(id).get().isActive()){
            throw new UserNotExistException(ExceptionMessages.USER_NOT_EXIST);
        }else{
            if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PART)){
                token = authorizationHeader.substring(7);
                String username = jwtUtil.getUsername(token);
                if (username.equalsIgnoreCase(userRepository.findById(id).get().getEmail())){
                    throw new AuthenticationException(ExceptionMessages.CANT_DELETE);
                }else {
                    UserEntity user = userRepository.findById(id).get();
                    user.setActive(true);
                    userRepository.save(user);
                    return userConverter.userToUserResponseDTO(user);
                }
            }
        }
        throw new AuthenticationException(ExceptionMessages.BAD_CREDENTIALS);
    }

    /**
     * Método encargado de actualizar el rol de un usuario en la aplicación
     * @param id
     * @param role
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO updateRole(Integer id, String role) {
        log.info("Inside user service method update role");
        if (userRepository.findById(id).isEmpty() || !userRepository.findById(id).get().isActive()){
            throw new UserNotExistException(ExceptionMessages.USER_NOT_EXIST);
        }else{
            if (role.equalsIgnoreCase(RoleType.USER.name()) || role.equalsIgnoreCase(RoleType.ADMIN.name())){
                UserEntity user = userRepository.findById(id).get();
                user.getRoleEntities().add(roleService.findRoleByType(RoleType.valueOf(role)));
                userRepository.save(user);
                return userConverter.userToUserResponseDTO(user);
            }else {
                throw new AuthenticationException(ExceptionMessages.ROLE_NOT_VALID);
            }
        }
    }

    /**
     * Método encargado de actualizar el usuario autenticado en la aplicación
     * @param updateUserRequestDTO
     * @param request
     * @param bindingResult
     * @return
     */
    @Override
    public UserResponseDTO updateUser(UpdateUserRequestDTO updateUserRequestDTO,
            BindingResult bindingResult, HttpServletRequest request) {
        log.info("Inside user service method update user ");

        if (bindingResult.hasErrors()){
            throw new AttributeErrorsException(ExceptionMessages.INVALID_ATTRIBUTES);
        }

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = "";
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PART)){
            token = authorizationHeader.substring(7);

            String username = jwtUtil.getUsername(token);
            if (userRepository.findByEmail(username).isPresent()){
                UserEntity user = userRepository.findByEmail(username).get();
                user.setFirstname(updateUserRequestDTO.getFirstname());
                user.setLastname(updateUserRequestDTO.getLastname());
                user.setPassword(encryptPassword(updateUserRequestDTO.getPassword()));

                return userConverter.userToUserResponseDTO(userRepository.save(user));
            }else{
                throw new UserNotExistException(ExceptionMessages.USER_NOT_EXIST);
            }

        }
        throw new AuthenticationException(ExceptionMessages.BAD_CREDENTIALS);
    }

    /**
     * Método encargado de encriptar las contraseñas
     * @param password
     * @return String
     */
    private String encryptPassword(String password) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(7);
        return passwordEncoder.encode(password);
    }

}
