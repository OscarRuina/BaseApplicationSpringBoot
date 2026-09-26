package com.organization.application.services.implementations;

import com.organization.application.configurations.email.service.IEmailService;
import com.organization.application.configurations.exceptions.ForbiddenException;
import com.organization.application.configurations.exceptions.UserAlreadyExistException;
import com.organization.application.configurations.exceptions.UserNotExistException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserService implements IUserService {

    private final IUserRepository userRepository;

    private final UserConverter userConverter;

    private final IRoleService roleService;

    private final IEmailService emailService;

    private final PasswordEncoder passwordEncoder;

    private static final  String EMAIL_SUBJECT = "Registro de Usuario ";

    public UserService(IUserRepository userRepository, UserConverter userConverter,
            IRoleService roleService,
            IEmailService emailService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
        this.roleService = roleService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
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
     * @param registerUserRequestDTO
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO register(RegisterUserRequestDTO registerUserRequestDTO) {
        log.info("Inside user service method register");
        if (userRepository.findByEmail(registerUserRequestDTO.getEmail()).isPresent()){
            throw new UserAlreadyExistException(ExceptionMessages.USER_ALREADY_EXIST);
        }else {
            if (!registerUserRequestDTO.getRole().equalsIgnoreCase(RoleType.USER.name())){
                throw new ForbiddenException(ExceptionMessages.ROLE_NOT_VALID);
            }
            RoleEntity role = roleService.findRoleByType(RoleType.valueOf(registerUserRequestDTO.getRole()));
            log.info(role.getType().name());
            if (registerUserRequestDTO.getRole().equalsIgnoreCase(RoleType.USER.name())){
                RandomStringGenerator generator = new RandomStringGenerator.Builder()
                        .withinRange('0', 'z')
                        .filteredBy(CharacterPredicates.DIGITS, CharacterPredicates.LETTERS)
                        .build();
                String temporaryPassword = generator.generate(8,12);
                UserResponseDTO dto =  userConverter.userToUserResponseDTO(
                        userRepository.save(
                                UserEntity.builder()
                                        .firstname(registerUserRequestDTO.getFirstname())
                                        .lastname(registerUserRequestDTO.getLastname())
                                        .email(registerUserRequestDTO.getEmail())
                                        .password(passwordEncoder.encode(temporaryPassword))
                                        .active(true)
                                        .roleEntities(Set.of(role))
                                        .build()
                        )
                );
                String[] toUser = {registerUserRequestDTO.getEmail()};
                Map<String, Object> message = new HashMap<>();
                message.put("username", registerUserRequestDTO.getEmail());
                message.put("password", temporaryPassword);
                CompletableFuture.runAsync(() -> emailService.sendEmail(toUser, EMAIL_SUBJECT, message));
                return dto;
            }else {
                log.error(ExceptionMessages.CANT_CREATE_USER);
                throw new ForbiddenException(ExceptionMessages.CANT_CREATE_USER);
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
     * @param callerEmail
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO delete(Integer id, String callerEmail) {
        log.info("Inside user service method delete user by id");

        UserEntity user = userRepository.findById(id).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        if (!user.isActive()){
            throw new UserNotExistException(ExceptionMessages.USER_NOT_EXIST);
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

        if (user.isActive()){
            throw new UserNotExistException(ExceptionMessages.USER_NOT_EXIST);
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
                throw new ForbiddenException(ExceptionMessages.ROLE_NOT_VALID);
            }
        }
    }

    /**
     * Método encargado de actualizar el usuario autenticado en la aplicación
     * @param updateUserRequestDTO
     * @param bindingResult
     * @param callerEmail
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO updateUser(UpdateUserRequestDTO updateUserRequestDTO, String callerEmail) {
        log.info("Inside user service method update user ");

        UserEntity user = userRepository.findByEmail(callerEmail).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        user.setFirstname(updateUserRequestDTO.getFirstname());
        user.setLastname(updateUserRequestDTO.getLastname());

        if (updateUserRequestDTO.getPassword() != null && !updateUserRequestDTO.getPassword().isBlank()){
            user.setPassword(passwordEncoder.encode(updateUserRequestDTO.getPassword()));
        }

        return userConverter.userToUserResponseDTO(userRepository.save(user));
    }
}
