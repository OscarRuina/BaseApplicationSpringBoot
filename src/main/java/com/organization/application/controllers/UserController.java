package com.organization.application.controllers;

import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.services.interfaces.IUserService;
import com.organization.application.utils.ApplicationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    public ResponseEntity<Object> me(HttpServletRequest request){
        try{
            return userService.me(request);
        }catch (Exception e){
            log.error("{}", e.getMessage());
        }
        return ApplicationResponse.getResponseEntity("ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Object> findUsers() {
        try{
            return userService.findUsers();
        }catch (Exception e){
            log.error("{}", e.getMessage());
        }
        return ApplicationResponse.getResponseEntity("ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Object> register(@Valid @RequestBody RegisterUserRequestDTO registerUserRequestDTO){
        try{
            return userService.register(registerUserRequestDTO);
        }catch (Exception e){
            log.error("{}", e.getMessage());
        }
        return ApplicationResponse.getResponseEntity("ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @GetMapping(value = "/active",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<Object> findUsersActive() {
        try{
            return userService.findUsersActive(true);
        }catch (Exception e){
            log.error("{}", e.getMessage());
        }
        return ApplicationResponse.getResponseEntity("ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @GetMapping(value = "/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<Object> findUser(@PathVariable(name = "id") Integer id) {
        try{
            return userService.findUser(id);
        }catch (Exception e){
            log.error("{}", e.getMessage());
        }
        return ApplicationResponse.getResponseEntity("ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @DeleteMapping(value = "/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Object> deleteUser(@PathVariable(name = "id") Integer id, HttpServletRequest request) {
        try{
            return userService.delete(id, request);
        }catch (Exception e){
            log.error("{}", e.getMessage());
        }
        return ApplicationResponse.getResponseEntity("ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @PutMapping(value = "/status/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Object> updateStatusUser(@PathVariable(name = "id") Integer id, HttpServletRequest request) {
        try{
            return userService.updateStatus(id,request);
        }catch (Exception e){
            log.error("{}", e.getMessage());
        }
        return ApplicationResponse.getResponseEntity("ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @PutMapping(value = "/roles/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Object> updateRoleUser(@PathVariable(name = "id") Integer id, @RequestParam(name = "role") String role) {
        try{
            return userService.updateRole(id,role);
        }catch (Exception e){
            log.error("{}", e.getMessage());
        }
        return ApplicationResponse.getResponseEntity("ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
