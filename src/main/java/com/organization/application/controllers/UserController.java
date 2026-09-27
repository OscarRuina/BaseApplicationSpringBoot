package com.organization.application.controllers;

import com.organization.application.configurations.security.service.UserPrincipal;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.request.UpdateUserRequestDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.messages.ConstantsMessages;
import com.organization.application.messages.ResponseMessages;
import com.organization.application.messages.SwaggerMessages;
import com.organization.application.models.enums.RoleType;
import com.organization.application.services.interfaces.IUserService;
import com.organization.application.dtos.response.ApplicationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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
            @ApiResponse(responseCode = "403", description = SwaggerMessages.ERROR_RESPONSE_403),
            @ApiResponse(responseCode = "404", description = SwaggerMessages.ERROR_RESPONSE_404),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> me(
            @AuthenticationPrincipal UserPrincipal principal){
        log.info("GET:api/users/me");
        UserResponseDTO dto =  userService.me(principal.getUsername());
        log.info(ResponseMessages.ME_SUCCESSFUL);
        return ResponseEntity.ok(new ApplicationResponse<>(dto,ResponseMessages.ME_SUCCESSFUL));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = SwaggerMessages.USER_ALL_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_ALL_RESPONSE_200),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "403", description = SwaggerMessages.ERROR_RESPONSE_403),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<List<UserResponseDTO>>> findUsers() {
        log.info("GET:api/users");
        List<UserResponseDTO> dto =  userService.findUsers();
        log.info(ResponseMessages.GET_USERS_SUCCESSFUL);
        return ResponseEntity.ok(new ApplicationResponse<>(dto,ResponseMessages.GET_USERS_SUCCESSFUL));
    }

    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = SwaggerMessages.USER_REGISTER_OPERATION)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RegisterUserRequestDTO.class)
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = SwaggerMessages.USER_REGISTER_RESPONSE_201),
            @ApiResponse(responseCode = "400", description = SwaggerMessages.ERROR_RESPONSE_400),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "403", description = SwaggerMessages.ERROR_RESPONSE_403),
            @ApiResponse(responseCode = "409", description = SwaggerMessages.ERROR_RESPONSE_409),
            @ApiResponse(responseCode = "502", description = SwaggerMessages.ERROR_RESPONSE_502),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> register(@Valid @RequestBody RegisterUserRequestDTO registerUserRequestDTO){
        log.info("POST:api/users/register");
        UserResponseDTO dto =  userService.register(registerUserRequestDTO);
        log.info(ResponseMessages.REGISTER_SUCCESSFUL);
        return ResponseEntity.created(
                        ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/users/{id}")
                                .buildAndExpand(dto.getId())
                                .toUri())
                .body(new ApplicationResponse<>(dto,ResponseMessages.REGISTER_SUCCESSFUL));
    }

    @GetMapping(value = "/active",produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = SwaggerMessages.USER_ALL_ACTIVE_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_ALL_ACTIVE_RESPONSE_200),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "403", description = SwaggerMessages.ERROR_RESPONSE_403),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<List<UserResponseDTO>>> findUsersActive() {
        log.info("GET:api/users/active");
        List<UserResponseDTO> dto =  userService.findActiveUsers();
        log.info(ResponseMessages.GET_ACTIVE_USERS_SUCCESSFUL);
        return ResponseEntity.ok(new ApplicationResponse<>(dto,ResponseMessages.GET_ACTIVE_USERS_SUCCESSFUL));
    }

    @GetMapping(value = "/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = SwaggerMessages.USER_FIND_ID_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_FIND_ID_RESPONSE_200),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "403", description = SwaggerMessages.ERROR_RESPONSE_403),
            @ApiResponse(responseCode = "404", description = SwaggerMessages.ERROR_RESPONSE_404),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> findUser(@PathVariable(name = "id") Integer id) {
        log.info("GET:api/users/{}", id);
        UserResponseDTO dto =  userService.findUser(id);
        log.info(ResponseMessages.GET_USER_SUCCESSFUL);
        return ResponseEntity.ok(new ApplicationResponse<>(dto,ResponseMessages.GET_USER_SUCCESSFUL));
    }

    @DeleteMapping(value = "/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = SwaggerMessages.USER_DELETE_ID_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_DELETE_ID_RESPONSE_200),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "403", description = SwaggerMessages.ERROR_RESPONSE_403),
            @ApiResponse(responseCode = "404", description = SwaggerMessages.ERROR_RESPONSE_404),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> deleteUser(
            @PathVariable(name = "id") Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("DELETE:api/users/{}", id);
        UserResponseDTO dto =  userService.delete(id,principal.getUsername());
        log.info(ResponseMessages.DELETE_USER_SUCCESSFUL);
        return ResponseEntity.ok(new ApplicationResponse<>(dto,ResponseMessages.DELETE_USER_SUCCESSFUL));
    }

    @PutMapping(value = "/status/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = SwaggerMessages.USER_UPDATE_STATUS_ID_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_UPDATE_STATUS_ID_RESPONSE_200),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "403", description = SwaggerMessages.ERROR_RESPONSE_403),
            @ApiResponse(responseCode = "404", description = SwaggerMessages.ERROR_RESPONSE_404),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> updateStatusUser(
            @PathVariable(name = "id") Integer id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("PUT:api/users/status/{}", id);
        UserResponseDTO dto =  userService.updateStatus(id,principal.getUsername());
        log.info(ResponseMessages.UPDATE_STATUS_SUCCESSFUL);
        return ResponseEntity.ok(new ApplicationResponse<>(dto,ResponseMessages.UPDATE_STATUS_SUCCESSFUL));
    }

    @PutMapping(value = "/roles/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = SwaggerMessages.USER_UPDATE_ROLE_ID_OPERATION)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_UPDATE_ROLE_ID_RESPONSE_200),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "403", description = SwaggerMessages.ERROR_RESPONSE_403),
            @ApiResponse(responseCode = "404", description = SwaggerMessages.ERROR_RESPONSE_404),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> updateRoleUser(@PathVariable(name = "id") Integer id, @RequestParam(name = "role") RoleType role) {
        log.info("PUT:api/users/roles/id");
        UserResponseDTO dto =  userService.updateRole(id,role);
        log.info(ResponseMessages.UPDATE_ROLE_SUCCESSFUL);
        return ResponseEntity.ok(new ApplicationResponse<>(dto,ResponseMessages.UPDATE_ROLE_SUCCESSFUL));
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = SwaggerMessages.USER_UPDATE_OPERATION)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UpdateUserRequestDTO.class)
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_UPDATE_RESPONSE_200),
            @ApiResponse(responseCode = "400", description = SwaggerMessages.ERROR_RESPONSE_400),
            @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
            @ApiResponse(responseCode = "403", description = SwaggerMessages.ERROR_RESPONSE_403),
            @ApiResponse(responseCode = "404", description = SwaggerMessages.ERROR_RESPONSE_404),
            @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
    })
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ApplicationResponse<UserResponseDTO>> updateUser(@Valid @RequestBody
            UpdateUserRequestDTO updateUserRequestDTO,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("PUT:api/users/");
        UserResponseDTO dto =  userService.updateUser(updateUserRequestDTO,
                principal.getUsername());
        log.info(ResponseMessages.UPDATE_USER_SUCCESSFUL);
        return ResponseEntity.ok(new ApplicationResponse<>(dto,ResponseMessages.UPDATE_USER_SUCCESSFUL));
    }
}
