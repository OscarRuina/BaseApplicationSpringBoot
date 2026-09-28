package com.organization.application.configurations.exceptions;

public class ForbiddenException extends RuntimeException{

    public ForbiddenException(String message){
        super(message);
    }
}
