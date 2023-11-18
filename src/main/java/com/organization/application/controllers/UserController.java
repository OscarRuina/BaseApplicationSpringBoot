package com.organization.application.controllers;

import com.organization.application.configurations.exceptions.AuthenticationException;
import com.organization.application.configurations.exceptions.UserAlreadyExistException;
import com.organization.application.configurations.exceptions.UserNotExistException;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.services.interfaces.IUserService;
import com.organization.application.utils.AppResponse;
import com.organization.application.utils.ApplicationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private IUserService userService;

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<AppResponse<LoginResponseDTO>> me(HttpServletRequest request){
        log.info("GET:api/users/me");
        try{
            LoginResponseDTO dto =  userService.me(request);
            log.info("Me Successful");
            return new ResponseEntity<>(new AppResponse<>(dto,"Me Successful"),HttpStatus.OK);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, "An error Occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<AppResponse<List<UserResponseDTO>>> findUsers() {
        log.info("GET:api/users");
        try{
            List<UserResponseDTO> dto =  userService.findUsers();
            log.info("Get Users Successful");
            return new ResponseEntity<>(new AppResponse<>(dto,"Get Users Successful"),HttpStatus.OK);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, "An error Occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<AppResponse<UserResponseDTO>> register(@Valid @RequestBody RegisterUserRequestDTO registerUserRequestDTO){
        log.info("POST:api/users/register");
        try{
            UserResponseDTO dto =  userService.register(registerUserRequestDTO);
            log.info("Register Successful");
            return new ResponseEntity<>(new AppResponse<>(dto,"Register Successful"),HttpStatus.OK);
        }catch (AuthenticationException | UserAlreadyExistException e) {
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, "An error Occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/active",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<AppResponse<List<UserResponseDTO>>> findUsersActive() {
        log.info("GET:api/users/active");
        try{
            List<UserResponseDTO> dto =  userService.findUsersActive(true);
            log.info("Get Active Users Successful");
            return new ResponseEntity<>(new AppResponse<>(dto,"Get Active Users Successful"),HttpStatus.OK);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, "An error Occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<AppResponse<UserResponseDTO>> findUser(@PathVariable(name = "id") Integer id) {
        log.info("GET:api/users/id");
        try{
            UserResponseDTO dto =  userService.findUser(id);
            log.info("Get User Successful");
            return new ResponseEntity<>(new AppResponse<>(dto,"Get User Successful"),HttpStatus.OK);
        }catch (UserNotExistException e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, "An error Occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value = "/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<AppResponse<UserResponseDTO>> deleteUser(@PathVariable(name = "id") Integer id, HttpServletRequest request) {
        log.info("DELETE:api/users/id");
        try{
            UserResponseDTO dto =  userService.delete(id,request);
            log.info("Delete Successful");
            return new ResponseEntity<>(new AppResponse<>(dto,"Delete Successful"),HttpStatus.OK);
        }catch (AuthenticationException | UserNotExistException e) {
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, "An error Occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(value = "/status/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<AppResponse<UserResponseDTO>> updateStatusUser(@PathVariable(name = "id") Integer id, HttpServletRequest request) {
        log.info("PUT:api/users/status/id");
        try{
            UserResponseDTO dto =  userService.updateStatus(id,request);
            log.info("Update Status Successful");
            return new ResponseEntity<>(new AppResponse<>(dto,"Update Status Successful"),HttpStatus.OK);
        }catch (AuthenticationException | UserNotExistException e) {
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, "An error Occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(value = "/roles/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<AppResponse<UserResponseDTO>> updateRoleUser(@PathVariable(name = "id") Integer id, @RequestParam(name = "role") String role) {
        log.info("PUT:api/users/roles/id");
        try{
            UserResponseDTO dto =  userService.updateRole(id,role);
            log.info("Update Role Successful");
            return new ResponseEntity<>(new AppResponse<>(dto,"Update Role Successful"),HttpStatus.OK);
        }catch (AuthenticationException | UserNotExistException e) {
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, e.getMessage()),
                    HttpStatus.BAD_REQUEST);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new AppResponse<>(null, "An error Occurred"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
