package com.organization.application.controllers;

import com.organization.application.dtos.response.ApplicationResponse;
import com.organization.application.messages.ResponseMessages;
import com.organization.application.services.interfaces.IGenericService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/objects")
@Slf4j
public class GenericController {

    private final IGenericService genericService;

    public GenericController(IGenericService genericService) {
        this.genericService = genericService;
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ApplicationResponse<Object>> getById(@RequestParam(name = "partialUrl", required = true) String partialUrl, @PathVariable(name = "id") Integer id){
        log.info("GET:api/objects/{}", id);
        try{
            Object dto = genericService.findObjectById(partialUrl,id);
            return new ResponseEntity<>(new ApplicationResponse<>(dto, "Post Get Successful"), HttpStatus.OK);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ApplicationResponse<List<Object>>> getAll(@RequestParam(name = "partialUrl", required = true) String partialUrl){
        log.info("GET:api/objects");
        try{
            List<Object> dto = genericService.findAll(partialUrl);
            return new ResponseEntity<>(new ApplicationResponse<>(dto, "Post Get All Successful"), HttpStatus.OK);
        }catch (RuntimeException e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.NO_CONTENT);
        }catch (Exception e){
            log.error("{}", e.getMessage());
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
