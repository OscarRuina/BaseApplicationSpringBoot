package com.organization.application.configurations.exceptions;

public class UserNotExistException extends RuntimeException{

    public UserNotExistException(String message){
        super(message);
    }
}
