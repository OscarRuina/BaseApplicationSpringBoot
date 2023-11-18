package com.organization.application.configurations.exceptions;

public class InvalidFilterTokenException extends RuntimeException{

    public InvalidFilterTokenException(String message, Throwable cause){
        super(message, cause);
    }

}
