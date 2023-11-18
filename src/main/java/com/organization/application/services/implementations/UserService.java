package com.organization.application.services.implementations;

import com.organization.application.configurations.exceptions.AuthenticationException;
import com.organization.application.configurations.exceptions.UserAlreadyExistException;
import com.organization.application.configurations.security.jwt.JwtUtil;
import com.organization.application.converters.UserConverter;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IUserRepository;
import com.organization.application.services.interfaces.IRoleService;
import com.organization.application.services.interfaces.IUserService;
import com.organization.application.utils.ApplicationResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Object> me(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = "";

        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PART)){
            token = authorizationHeader.substring(7);

            return ApplicationResponse.getResponseEntity(
                    userConverter.userToLoginResponseDTO(userDetailsService.getUser(), token)
                    , HttpStatus.OK);
        }

        return ApplicationResponse.getResponseEntity("ERROR Bad Credentials", HttpStatus.BAD_REQUEST);
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
    public ResponseEntity<Object> findUsers() {
        return ApplicationResponse.getResponseEntity(
                userRepository.findAll().stream()
                        .map(user -> userConverter.userToUserResponseDTO(user))
                        .collect(Collectors.toList())
                , HttpStatus.OK
        );
    }

    @Override
    public ResponseEntity<Object> findUsersActive(boolean active) {
        return ApplicationResponse.getResponseEntity(
                userRepository.findAllByActive(active).stream()
                        .map(user -> userConverter.userToUserResponseDTO(user))
                        .collect(Collectors.toList())
                , HttpStatus.OK
        );
    }

    @Override
    public ResponseEntity<Object> findUser(Integer id) {
        if (userRepository.findById(id).isEmpty()){
            return ApplicationResponse.getResponseEntity("ERROR User Not Exist", HttpStatus.BAD_REQUEST);
        }else {
            return ApplicationResponse.getResponseEntity(
                    userConverter.userToUserResponseDTO(
                            userRepository.findById(id).get()
                    )
                    , HttpStatus.OK
            );
        }
    }

    @Override
    public ResponseEntity<Object> delete(Integer id, HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = "";

        if (userRepository.findById(id).isEmpty() || !userRepository.findById(id).get().isActive()){
            return ApplicationResponse.getResponseEntity("ERROR User Not Exist or it Has Eliminated", HttpStatus.BAD_REQUEST);
        }else{
            if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PART)){
                token = authorizationHeader.substring(7);
                String username = jwtUtil.getUsername(token);
                if (username.equalsIgnoreCase(userRepository.findById(id).get().getEmail())){
                    return ApplicationResponse.getResponseEntity("ERROR Can't Delete ", HttpStatus.BAD_REQUEST);
                }else {
                    UserEntity user = userRepository.findById(id).get();
                    user.setActive(false);
                    userRepository.save(user);
                    return ApplicationResponse.getResponseEntity(
                            userConverter.userToUserResponseDTO(user)
                            , HttpStatus.OK
                    );
                }
            }
        }
        return ApplicationResponse.getResponseEntity("ERROR Bad Credentials", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ResponseEntity<Object> updateStatus(Integer id, HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = "";

        if (userRepository.findById(id).isEmpty() || userRepository.findById(id).get().isActive()){
            return ApplicationResponse.getResponseEntity("ERROR User Not Exist or it Has Not Eliminated", HttpStatus.BAD_REQUEST);
        }else{
            if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PART)){
                token = authorizationHeader.substring(7);
                String username = jwtUtil.getUsername(token);
                if (username.equalsIgnoreCase(userRepository.findById(id).get().getEmail())){
                    return ApplicationResponse.getResponseEntity("ERROR Can't Update ", HttpStatus.BAD_REQUEST);
                }else {
                    UserEntity user = userRepository.findById(id).get();
                    user.setActive(true);
                    userRepository.save(user);
                    return ApplicationResponse.getResponseEntity(
                            userConverter.userToUserResponseDTO(user)
                            , HttpStatus.OK
                    );
                }
            }
        }
        return ApplicationResponse.getResponseEntity("ERROR Bad Credentials", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ResponseEntity<Object> updateRole(Integer id, String role) {
        if (userRepository.findById(id).isEmpty() || !userRepository.findById(id).get().isActive()){
            return ApplicationResponse.getResponseEntity("ERROR User Not Exist or it Has Eliminated", HttpStatus.BAD_REQUEST);
        }else{
            if (role.equalsIgnoreCase(RoleType.USER.name()) || role.equalsIgnoreCase(RoleType.ADMIN.name())){
                UserEntity user = userRepository.findById(id).get();
                user.getRoleEntities().add(roleService.findRoleByType(RoleType.valueOf(role)));
                userRepository.save(user);
                return ApplicationResponse.getResponseEntity(
                        userConverter.userToUserResponseDTO(user)
                        , HttpStatus.OK
                );
            }else {
                return ApplicationResponse.getResponseEntity("ERROR Invalid Role", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private String encryptPassword(String password) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(7);
        return passwordEncoder.encode(password);
    }

}
