package com.organization.application.controllers;

import com.organization.application.configurations.exceptions.AttributeErrorsException;
import com.organization.application.configurations.exceptions.AuthenticationException;
import com.organization.application.configurations.exceptions.UserAlreadyExistException;
import com.organization.application.configurations.exceptions.UserNotExistException;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.request.UpdateUserRequestDTO;
import com.organization.application.dtos.response.LoginResponseDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.messages.ConstantsMessages;
import com.organization.application.messages.ResponseMessages;
import com.organization.application.messages.SwaggerMessages;
import com.organization.application.services.interfaces.IUserService;
import com.organization.application.dtos.response.ApplicationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
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
@SecurityRequirement(name = ConstantsMessages.SWAGGER_SECURITY_SCHEME_NAME)
@Tag(name = "User Controller")
public class UserController {

    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = SwaggerMessages.USER_ME_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_ME_RESPONSE_200),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
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
    @Operation(summary = SwaggerMessages.USER_ALL_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_ALL_RESPONSE_200),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
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
    @Operation(summary = SwaggerMessages.USER_REGISTER_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_REGISTER_RESPONSE_200),
            @ApiResponse(responseCode = "400", description = SwaggerMessages.ERROR_RESPONSE_400),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> register(@Valid @RequestBody RegisterUserRequestDTO registerUserRequestDTO,
            BindingResult bindingResult){
        log.info("POST:api/users/register");
        try{
            UserResponseDTO dto =  userService.register(registerUserRequestDTO, bindingResult);
            log.info(ResponseMessages.REGISTER_SUCCESSFUL);
            return new ResponseEntity<>(new ApplicationResponse<>(dto,ResponseMessages.REGISTER_SUCCESSFUL),HttpStatus.OK);
        }catch (AuthenticationException | UserAlreadyExistException | AttributeErrorsException e) {
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
    @Operation(summary = SwaggerMessages.USER_ALL_ACTIVE_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_ALL_ACTIVE_RESPONSE_200),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
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
    @Operation(summary = SwaggerMessages.USER_FIND_ID_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_FIND_ID_RESPONSE_200),
            @ApiResponse(responseCode = "400", description = SwaggerMessages.ERROR_RESPONSE_400),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> findUser(@PathVariable(name = "id") Integer id) {
        log.info("GET:api/users/{}", id);
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
    @Operation(summary = SwaggerMessages.USER_DELETE_ID_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_DELETE_ID_RESPONSE_200),
            @ApiResponse(responseCode = "400", description = SwaggerMessages.ERROR_RESPONSE_400),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> deleteUser(@PathVariable(name = "id") Integer id, HttpServletRequest request) {
        log.info("DELETE:api/users/{}", id);
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
    @Operation(summary = SwaggerMessages.USER_UPDATE_STATUS_ID_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_UPDATE_STATUS_ID_RESPONSE_200),
            @ApiResponse(responseCode = "400", description = SwaggerMessages.ERROR_RESPONSE_400),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> updateStatusUser(@PathVariable(name = "id") Integer id, HttpServletRequest request) {
        log.info("PUT:api/users/status/{}", id);
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
    @Operation(summary = SwaggerMessages.USER_UPDATE_ROLE_ID_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_UPDATE_ROLE_ID_RESPONSE_200),
            @ApiResponse(responseCode = "400", description = SwaggerMessages.ERROR_RESPONSE_400),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
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

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = SwaggerMessages.USER_UPDATE_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_UPDATE_RESPONSE_200),
            @ApiResponse(responseCode = "400", description = SwaggerMessages.ERROR_RESPONSE_400),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> updateUser(@Valid @RequestBody
            UpdateUserRequestDTO updateUserRequestDTO, BindingResult bindingResult, HttpServletRequest request) {
        log.info("PUT:api/users/");
        try{
            UserResponseDTO dto =  userService.updateUser(updateUserRequestDTO,bindingResult,request);
            log.info(ResponseMessages.UPDATE_USER_SUCCESSFUL);
            return new ResponseEntity<>(new ApplicationResponse<>(dto,ResponseMessages.UPDATE_USER_SUCCESSFUL),HttpStatus.OK);
        }catch (AttributeErrorsException | AuthenticationException | UserNotExistException  e) {
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
