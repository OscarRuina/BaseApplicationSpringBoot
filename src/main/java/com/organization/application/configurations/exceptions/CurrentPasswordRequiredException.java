package com.organization.application.configurations.exceptions;

public class CurrentPasswordRequiredException extends RuntimeException {

    public CurrentPasswordRequiredException(String message){
        super(message);
    }
}
