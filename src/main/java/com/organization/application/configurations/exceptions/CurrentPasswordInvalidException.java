package com.organization.application.configurations.exceptions;

public class CurrentPasswordInvalidException extends RuntimeException {

    public CurrentPasswordInvalidException(String message){
        super(message);
    }
}
