package com.organization.application.configurations.exceptions;

public class AuthenticationServiceUnavailableException extends RuntimeException {

    public AuthenticationServiceUnavailableException(String message){
        super(message);
    }
}
