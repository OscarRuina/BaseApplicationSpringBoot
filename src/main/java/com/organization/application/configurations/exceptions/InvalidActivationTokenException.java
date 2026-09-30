package com.organization.application.configurations.exceptions;

/**
 * El token no corresponde a ningún registro. Responde 400 y no distingue entre token inexistente y token con formato inválido: distinguirlos le daría a un atacante una forma de sondear qué hashes existen.
 */
public class InvalidActivationTokenException extends RuntimeException {

    public InvalidActivationTokenException(String message){
        super(message);
    }
}
