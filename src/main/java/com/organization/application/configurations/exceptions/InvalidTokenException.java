package com.organization.application.configurations.exceptions;

public class InvalidTokenException extends RuntimeException{

    public InvalidTokenException(String message, Throwable cause){
        super(message, cause);
    }

}
