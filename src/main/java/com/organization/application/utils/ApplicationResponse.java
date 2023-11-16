package com.organization.application.utils;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ApplicationResponse {

    private ApplicationResponse(){}

    public static ResponseEntity<Object> getResponseEntity(Object object, HttpStatus httpStatus){
        return new ResponseEntity<>(object, httpStatus);
    }

}
