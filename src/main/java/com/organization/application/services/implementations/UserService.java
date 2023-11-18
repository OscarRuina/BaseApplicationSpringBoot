package com.organization.application.services.implementations;

import com.organization.application.configurations.exceptions.AuthenticationException;
import com.organization.application.configurations.exceptions.UserAlreadyExistException;
import com.organization.application.configurations.exceptions.UserNotExistException;
import com.organization.application.configurations.security.jwt.JwtUtil;
import com.organization.application.converters.UserConverter;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IUserRepository;
import com.organization.application.services.interfaces.IRoleService;
import com.organization.application.services.interfaces.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserService implements IUserService {

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private UserConverter userConverter;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private IRoleService roleService;

    @Autowired
    private JwtUtil jwtUtil;

    private final static String BEARER_PART = "Bearer ";

    @Override
    public LoginResponseDTO me(HttpServletRequest request) {
        log.info("Inside user service method me ");

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = "";

        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PART)){
            token = authorizationHeader.substring(7);

            return userConverter.userToLoginResponseDTO(userDetailsService.getUser(), token);
        }
        throw new AuthenticationException("ERROR Bad Credentials");
    }

    @Override
    public UserResponseDTO register(RegisterUserRequestDTO registerUserRequestDTO) {
        log.info("Inside user service method register");
        if (userRepository.findByEmail(registerUserRequestDTO.getEmail()).isPresent()){
            throw new UserAlreadyExistException("ERROR User Already Exist");
        }else {
            if (!registerUserRequestDTO.getRole().equalsIgnoreCase(RoleType.USER.name())){
                throw new AuthenticationException("ERROR Role Not Valid");
            }
            RoleEntity role = roleService.findRoleByType(RoleType.valueOf(registerUserRequestDTO.getRole()));
            log.info(role.getType().name());
            if (registerUserRequestDTO.getRole().equalsIgnoreCase(RoleType.USER.name())){
                return userConverter.userToUserResponseDTO(
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
            }else {
                log.error("ERROR Cant Create User");
                throw new AuthenticationException("ERROR Cant Create User");
            }
        }
    }

    @Override
    public List<UserResponseDTO> findUsers() {
        log.info("Inside user service method find users");
        return userRepository.findAll().stream()
                .map(user -> userConverter.userToUserResponseDTO(user))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponseDTO> findUsersActive(boolean active) {
        log.info("Inside user service method find active users");
        return userRepository.findAllByActive(active).stream()
                .map(user -> userConverter.userToUserResponseDTO(user))
                .collect(Collectors.toList());
    }

    @Override
    public UserResponseDTO findUser(Integer id) {
        log.info("Inside user service method find user by id");
        if (userRepository.findById(id).isEmpty()){
            throw new UserNotExistException("ERROR User Not Exist");
        }else {
            return userConverter.userToUserResponseDTO(
                    userRepository.findById(id).get()
            );
        }
    }

    @Override
    public UserResponseDTO delete(Integer id, HttpServletRequest request) {
        log.info("Inside user service method delete user by id");
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = "";

        if (userRepository.findById(id).isEmpty() || !userRepository.findById(id).get().isActive()){
            throw new UserNotExistException("ERROR User Not Exist or its Has Eliminated");
        }else{
            if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PART)){
                token = authorizationHeader.substring(7);
                String username = jwtUtil.getUsername(token);
                if (username.equalsIgnoreCase(userRepository.findById(id).get().getEmail())){
                    throw new AuthenticationException("ERROR Cant Delete");
                }else {
                    UserEntity user = userRepository.findById(id).get();
                    user.setActive(false);
                    userRepository.save(user);
                    return userConverter.userToUserResponseDTO(user);
                }
            }
        }
        throw new AuthenticationException("ERROR Bad Credentials");
    }

    @Override
    public UserResponseDTO updateStatus(Integer id, HttpServletRequest request) {
        log.info("Inside user service method update status user by id");
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = "";

        if (userRepository.findById(id).isEmpty() || userRepository.findById(id).get().isActive()){
            throw new UserNotExistException("ERROR User Not Exist or its Has Not Eliminated");
        }else{
            if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PART)){
                token = authorizationHeader.substring(7);
                String username = jwtUtil.getUsername(token);
                if (username.equalsIgnoreCase(userRepository.findById(id).get().getEmail())){
                    throw new AuthenticationException("ERROR Cant Delete");
                }else {
                    UserEntity user = userRepository.findById(id).get();
                    user.setActive(true);
                    userRepository.save(user);
                    return userConverter.userToUserResponseDTO(user);
                }
            }
        }
        throw new AuthenticationException("ERROR Bad Credentials");
    }

    @Override
    public UserResponseDTO updateRole(Integer id, String role) {
        log.info("Inside user service method update role");
        if (userRepository.findById(id).isEmpty() || !userRepository.findById(id).get().isActive()){
            throw new UserNotExistException("ERROR User Not Exist or its Has Eliminated");
        }else{
            if (role.equalsIgnoreCase(RoleType.USER.name()) || role.equalsIgnoreCase(RoleType.ADMIN.name())){
                UserEntity user = userRepository.findById(id).get();
                user.getRoleEntities().add(roleService.findRoleByType(RoleType.valueOf(role)));
                userRepository.save(user);
                return userConverter.userToUserResponseDTO(user);
            }else {
                throw new AuthenticationException("ERROR Role Not Valid");
            }
        }
    }

    private String encryptPassword(String password) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(7);
        return passwordEncoder.encode(password);
    }

}
