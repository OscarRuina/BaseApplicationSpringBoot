package com.organization.application.services.implementations;

import com.organization.application.dtos.response.PostResponseDTO;
import com.organization.application.services.interfaces.IPostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class PostService implements IPostService {

    @Value("${BASE_URL}")
    private String api_url;

    private final RestTemplate restTemplate;

    public PostService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public PostResponseDTO findPostById(Integer id) {
        String url = api_url + "posts/" + id;
        return restTemplate.getForObject(url, PostResponseDTO.class);
    }

    @Override
    public List<PostResponseDTO> findAll(String partialUrl) {
        String url = api_url + partialUrl;

        return null;
    }
}
