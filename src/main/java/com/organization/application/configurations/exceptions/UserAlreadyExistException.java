package com.organization.application.configurations.exceptions;

public class UserAlreadyExistException extends RuntimeException{

    public UserAlreadyExistException(String message){
        super(message);
    }
}
