package com.organization.application.configurations.exceptions;

public class UserInactiveException extends RuntimeException {

    public UserInactiveException(String message){
        super(message);
    }
}
