package com.organization.application.controllers;

import com.organization.application.dtos.response.ApplicationResponse;
import com.organization.application.dtos.response.PostResponseDTO;
import com.organization.application.messages.ResponseMessages;
import com.organization.application.services.interfaces.IPostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/posts")
public class PostController {

    private final IPostService postService;


    public PostController(IPostService postService) {
        this.postService = postService;
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ApplicationResponse<PostResponseDTO>> getById(@PathVariable(name = "id") Integer id){
        try{
            PostResponseDTO dto = postService.findPostById(id);
            return new ResponseEntity<>(new ApplicationResponse<>(dto, "Post OK"), HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>(new ApplicationResponse<>(null, ResponseMessages.ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
