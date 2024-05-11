package com.organization.application.services.interfaces;

import com.organization.application.dtos.response.PostResponseDTO;

import java.util.List;

public interface IPostService {

    PostResponseDTO findPostById(Integer id);

    List<PostResponseDTO> findAll(String partialUrl);
}
