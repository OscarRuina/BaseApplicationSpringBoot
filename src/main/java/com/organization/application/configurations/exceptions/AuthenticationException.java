package com.organization.application.configurations.exceptions;

public class AuthenticationException extends RuntimeException{

    public AuthenticationException(String message){
        super(message);
    }

}
