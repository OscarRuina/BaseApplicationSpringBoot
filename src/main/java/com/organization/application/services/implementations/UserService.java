package com.organization.application.services.implementations;

import com.organization.application.configurations.email.service.IEmailService;
import com.organization.application.configurations.exceptions.CurrentPasswordInvalidException;
import com.organization.application.configurations.exceptions.CurrentPasswordRequiredException;
import com.organization.application.configurations.exceptions.ForbiddenException;
import com.organization.application.configurations.exceptions.InvalidRoleException;
import com.organization.application.configurations.exceptions.TooManyAttemptsException;
import com.organization.application.configurations.exceptions.UserAlreadyExistException;
import com.organization.application.configurations.exceptions.UserInactiveException;
import com.organization.application.configurations.exceptions.UserNotExistException;
import com.organization.application.configurations.security.throttle.LoginThrottle;
import com.organization.application.converters.UserConverter;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.request.UpdateUserRequestDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.messages.ExceptionMessages;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IUserRepository;
import com.organization.application.services.interfaces.IRoleService;
import com.organization.application.services.interfaces.IUserService;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UserService implements IUserService {

    private final IUserRepository userRepository;

    private final UserConverter userConverter;

    private final IRoleService roleService;

    private final IEmailService emailService;

    private final PasswordEncoder passwordEncoder;

    private final LoginThrottle loginThrottle;

    private static final String EMAIL_SUBJECT = "Registro de Usuario";

    private static final String TEMPLATE_NEW_USER = "email_new_user";

    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private static final int TEMPORARY_PASSWORD_LENGTH = 16;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public UserService(IUserRepository userRepository, UserConverter userConverter,
            IRoleService roleService,
            IEmailService emailService, PasswordEncoder passwordEncoder,
            LoginThrottle loginThrottle) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
        this.roleService = roleService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.loginThrottle = loginThrottle;
    }

    /**
     * Método encargado de retornar los datos del usuario autenticado en la aplicación
     * @param callerEmail
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO me(String callerEmail) {
        log.info("Inside user service method me ");

        UserEntity user = userRepository.findByEmail(callerEmail).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        return userConverter.userToUserResponseDTO(user);
    }

    /**
     * Método encargado de crear un nuevo usuario en la aplicación
     * La creación del usuario y el envío del mail ocurren dentro de la misma transacción,
     * por lo que un fallo en el envío revierte el alta y no queda ningún usuario persistido
     * @param registerUserRequestDTO
     * @return UserResponseDTO
     */
    @Override
    @Transactional
    public UserResponseDTO register(RegisterUserRequestDTO registerUserRequestDTO) {
        log.info("Inside user service method register");
        if (userRepository.findByEmail(registerUserRequestDTO.getEmail()).isPresent()){
            throw new UserAlreadyExistException(ExceptionMessages.USER_ALREADY_EXIST);
        }else {
            if (registerUserRequestDTO.getRole() != RoleType.USER){
                throw new InvalidRoleException(ExceptionMessages.ROLE_NOT_VALID);
            }
            RoleEntity role = roleService.findRoleByType(registerUserRequestDTO.getRole());
            log.info(role.getType().name());

            String temporaryPassword = generateTemporaryPassword();
            UserEntity user = UserEntity.builder()
                    .firstname(registerUserRequestDTO.getFirstname())
                    .lastname(registerUserRequestDTO.getLastname())
                    .email(registerUserRequestDTO.getEmail())
                    .password(passwordEncoder.encode(temporaryPassword))
                    .active(true)
                    .roleEntities(Set.of(role))
                    .build();

            try {
                user = userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException e) {
                log.warn("Concurrent registration detected for email {}",
                        registerUserRequestDTO.getEmail());
                throw new UserAlreadyExistException(ExceptionMessages.USER_ALREADY_EXIST);
            }

            String[] toUser = {registerUserRequestDTO.getEmail()};
            Map<String, Object> message = new HashMap<>();
            message.put("username", registerUserRequestDTO.getEmail());
            message.put("password", temporaryPassword);
            emailService.sendEmail(toUser, EMAIL_SUBJECT, TEMPLATE_NEW_USER, message);
            return userConverter.userToUserResponseDTO(user);
        }
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET[SECURE_RANDOM.nextInt(PASSWORD_ALPHABET.length)]);
        }
        return password.toString();
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
     * @return List<UserResponseDTO>
     */
    @Override
    public List<UserResponseDTO> findActiveUsers() {
        log.info("Inside user service method find active users");
        return userRepository.findAllByActive(true).stream()
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
     * @param callerEmail
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO delete(Integer id, String callerEmail) {
        log.info("Inside user service method delete user by id");

        UserEntity user = userRepository.findById(id).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        if (!user.isActive()){
            throw new UserInactiveException(ExceptionMessages.USER_NOT_ACTIVE);
        }

        if (user.getRoleEntities().stream().anyMatch(
                roleEntity -> roleEntity.getType().name().equalsIgnoreCase(RoleType.ADMIN.name()))){
            throw new ForbiddenException(ExceptionMessages.CANT_DELETE);
        }

        if (user.getEmail().equalsIgnoreCase(callerEmail)){
            throw new ForbiddenException(ExceptionMessages.CANT_DELETE);
        }

        user.setActive(false);
        userRepository.save(user);
        return userConverter.userToUserResponseDTO(user);
    }

    /**
     * Método encargado de actualizar el estado de un usuario en la aplicación
     * @param id
     * @param callerEmail
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO updateStatus(Integer id, String callerEmail) {
        log.info("Inside user service method update status user by id");

        UserEntity user = userRepository.findById(id).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        if (!user.isActive()){
            throw new UserInactiveException(ExceptionMessages.USER_NOT_ACTIVE);
        }

        if (user.getEmail().equalsIgnoreCase(callerEmail)){
            throw new ForbiddenException(ExceptionMessages.CANT_UPDATE_STATUS);
        }

        user.setActive(true);
        userRepository.save(user);
        return userConverter.userToUserResponseDTO(user);
    }

    /**
     * Método encargado de actualizar el rol de un usuario en la aplicación
     * @param id
     * @param role
     * @param callerEmail
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO updateRole(Integer id, RoleType role, String callerEmail) {
        log.info("Inside user service method update role");

        UserEntity user = userRepository.findById(id).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        if (!user.isActive()){
            throw new UserInactiveException(ExceptionMessages.USER_NOT_ACTIVE);
        }

        if (user.getEmail().equalsIgnoreCase(callerEmail)){
            throw new ForbiddenException(ExceptionMessages.CANT_UPDATE_ROLE);
        }

        RoleEntity roleEntity = roleService.findRoleByType(role);

        user.getRoleEntities().clear();
        user.getRoleEntities().add(roleEntity);

        return userConverter.userToUserResponseDTO(userRepository.save(user));
    }

    /**
     * Método encargado de actualizar el usuario autenticado en la aplicación
     * @param updateUserRequestDTO
     * @param callerEmail
     * @param clientIp
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO updateUser(UpdateUserRequestDTO updateUserRequestDTO, String callerEmail,
            String clientIp) {
        String newPassword = updateUserRequestDTO.getPassword();
        boolean passwordChange = newPassword != null && !newPassword.isBlank();

        if (passwordChange) {
            long retryAfter = loginThrottle.retryAfterSeconds(callerEmail, clientIp);
            if (retryAfter > 0) {
                throw new TooManyAttemptsException(ExceptionMessages.TOO_MANY_ATTEMPTS, retryAfter);
            }
            if (updateUserRequestDTO.getCurrentPassword() == null
                    || updateUserRequestDTO.getCurrentPassword().isBlank()) {
                throw new CurrentPasswordRequiredException(
                        ExceptionMessages.CURRENT_PASSWORD_REQUIRED);
            }
        }

        UserEntity user = userRepository.findByEmail(callerEmail).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        if (passwordChange && !passwordEncoder.matches(updateUserRequestDTO.getCurrentPassword(),
                user.getPassword())) {
            loginThrottle.recordFailure(callerEmail, clientIp);
            throw new CurrentPasswordInvalidException(ExceptionMessages.CURRENT_PASSWORD_INVALID);
        }

        user.setFirstname(updateUserRequestDTO.getFirstname());
        user.setLastname(updateUserRequestDTO.getLastname());

        if (passwordChange) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        UserResponseDTO dto = userConverter.userToUserResponseDTO(userRepository.save(user));

        if (passwordChange) {
            loginThrottle.recordSuccess(callerEmail);
        }

        return dto;
    }
}
