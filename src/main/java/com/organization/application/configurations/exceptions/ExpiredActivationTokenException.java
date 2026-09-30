package com.organization.application.configurations.exceptions;

/**
 * Pasaron los siete días. Es 410 y no 400 a propósito: el recurso existió y ya no va a volver, así que reintentar el mismo token no sirve.
 */
public class ExpiredActivationTokenException extends RuntimeException {

    public ExpiredActivationTokenException(String message){
        super(message);
    }
}
