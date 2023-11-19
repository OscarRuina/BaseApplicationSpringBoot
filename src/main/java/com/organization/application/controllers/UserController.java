package com.organization.application.controllers;

import com.organization.application.configurations.exceptions.AuthenticationException;
import com.organization.application.configurations.exceptions.UserAlreadyExistException;
import com.organization.application.configurations.exceptions.UserNotExistException;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.messages.ResponseMessages;
import com.organization.application.services.interfaces.IUserService;
import com.organization.application.dtos.response.ApplicationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {

    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ApplicationResponse<LoginResponseDTO>> me(HttpServletRequest request){
        log.info("GET:api/users/me");
        try{
            LoginResponseDTO dto =  userService.me(request);
            log.info(ResponseMessages.ME_SUCCESSFUL);
            return new ResponseEntity<>(new ApplicationResponse<>(dto,ResponseMessages.ME_SUCCESSFUL),HttpStatus.OK);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<List<UserResponseDTO>>> findUsers() {
        log.info("GET:api/users");
        try{
            List<UserResponseDTO> dto =  userService.findUsers();
            log.info(ResponseMessages.GET_USERS_SUCCESSFUL);
            return new ResponseEntity<>(new ApplicationResponse<>(dto,ResponseMessages.GET_USERS_SUCCESSFUL),HttpStatus.OK);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> register(@Valid @RequestBody RegisterUserRequestDTO registerUserRequestDTO){
        log.info("POST:api/users/register");
        try{
            UserResponseDTO dto =  userService.register(registerUserRequestDTO);
            log.info(ResponseMessages.REGISTER_SUCCESSFUL);
            return new ResponseEntity<>(new ApplicationResponse<>(dto,ResponseMessages.REGISTER_SUCCESSFUL),HttpStatus.OK);
        }catch (AuthenticationException | UserAlreadyExistException e) {
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/active",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ApplicationResponse<List<UserResponseDTO>>> findUsersActive() {
        log.info("GET:api/users/active");
        try{
            List<UserResponseDTO> dto =  userService.findUsersActive(true);
            log.info(ResponseMessages.GET_ACTIVE_USERS_SUCCESSFUL);
            return new ResponseEntity<>(new ApplicationResponse<>(dto,ResponseMessages.GET_ACTIVE_USERS_SUCCESSFUL),HttpStatus.OK);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> findUser(@PathVariable(name = "id") Integer id) {
        log.info("GET:api/users/id");
        try{
            UserResponseDTO dto =  userService.findUser(id);
            log.info(ResponseMessages.GET_USER_SUCCESSFUL);
            return new ResponseEntity<>(new ApplicationResponse<>(dto,ResponseMessages.GET_USER_SUCCESSFUL),HttpStatus.OK);
        }catch (UserNotExistException e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value = "/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> deleteUser(@PathVariable(name = "id") Integer id, HttpServletRequest request) {
        log.info("DELETE:api/users/id");
        try{
            UserResponseDTO dto =  userService.delete(id,request);
            log.info(ResponseMessages.DELETE_USER_SUCCESSFUL);
            return new ResponseEntity<>(new ApplicationResponse<>(dto,ResponseMessages.DELETE_USER_SUCCESSFUL),HttpStatus.OK);
        }catch (AuthenticationException | UserNotExistException e) {
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(value = "/status/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> updateStatusUser(@PathVariable(name = "id") Integer id, HttpServletRequest request) {
        log.info("PUT:api/users/status/id");
        try{
            UserResponseDTO dto =  userService.updateStatus(id,request);
            log.info(ResponseMessages.UPDATE_STATUS_SUCCESSFUL);
            return new ResponseEntity<>(new ApplicationResponse<>(dto,ResponseMessages.UPDATE_STATUS_SUCCESSFUL),HttpStatus.OK);
        }catch (AuthenticationException | UserNotExistException e) {
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(value = "/roles/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> updateRoleUser(@PathVariable(name = "id") Integer id, @RequestParam(name = "role") String role) {
        log.info("PUT:api/users/roles/id");
        try{
            UserResponseDTO dto =  userService.updateRole(id,role);
            log.info(ResponseMessages.UPDATE_ROLE_SUCCESSFUL);
            return new ResponseEntity<>(new ApplicationResponse<>(dto,ResponseMessages.UPDATE_ROLE_SUCCESSFUL),HttpStatus.OK);
        }catch (AuthenticationException | UserNotExistException e) {
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
