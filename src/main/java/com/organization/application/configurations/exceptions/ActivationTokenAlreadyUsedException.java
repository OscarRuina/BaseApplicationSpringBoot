package com.organization.application.configurations.exceptions;

/**
 * El token es de un solo uso. La cuenta ya está activada, así que el token está quemado y no sirve para volver a cambiar la contraseña.
 */
public class ActivationTokenAlreadyUsedException extends RuntimeException {

    public ActivationTokenAlreadyUsedException(String message){
        super(message);
    }
}
