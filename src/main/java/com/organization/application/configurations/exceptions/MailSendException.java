package com.organization.application.configurations.exceptions;

public class MailSendException extends RuntimeException{

    public MailSendException(String message){
        super(message);
    }

    public MailSendException(String message, Throwable cause){
        super(message, cause);
    }
}
