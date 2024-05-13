package com.organization.application.services.implementations;

import com.organization.application.services.interfaces.IGenericService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class GenericService implements IGenericService {

    @Value("${BASE_URL}")
    private String api_url;

    private final RestTemplate restTemplate;

    public GenericService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Object findObjectById(String partialUrl, Integer id) {
        log.info("Inside Service post, method findById, base URL: " + api_url);
        String url = api_url + "/"+ partialUrl +"/" + id;
        log.info("URL: " + url);
        return restTemplate.getForObject(url, Object.class);
    }

    @Override
    public List<Object> findAll(String partialUrl) {
        log.info("Inside Service post, method findAll, base URL: " + api_url);
        String url = api_url + "/" + partialUrl;
        log.info("URL: " + url);
        Object[] posts = restTemplate.getForObject(url, Object[].class);
        if( posts != null){
            return Arrays.asList(posts);
        }
        else {
            log.error("Empty List");
            throw new RuntimeException("ERROR List is empty");
        }
    }
}
