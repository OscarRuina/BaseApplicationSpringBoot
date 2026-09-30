package com.organization.application.configurations.exceptions;

/**
 * La cuenta nunca confirmó su registro, así que su estado no lo gobierna un administrador:
 * la deja inactiva hasta que el usuario siga el enlace de activación. Suspenderla o
 * cancelarla desde el panel la dejaría en un estado del que nadie puede salir.
 */
public class PendingActivationException extends RuntimeException {

    public PendingActivationException(String message){
        super(message);
    }
}
