package com.organization.application.configurations.exceptions;

public class InvalidRoleException extends RuntimeException {

    public InvalidRoleException(String message){
        super(message);
    }
}
