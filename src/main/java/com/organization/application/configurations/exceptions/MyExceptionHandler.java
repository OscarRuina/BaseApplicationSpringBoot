package com.organization.application.configurations.exceptions;

import com.organization.application.dtos.response.ApplicationResponse;
import com.organization.application.messages.ExceptionMessages;
import com.organization.application.messages.ResponseMessages;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class MyExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Map<Integer, String> STATUS_MESSAGES = Map.of(
            HttpStatus.BAD_REQUEST.value(), ExceptionMessages.INVALID_REQUEST,
            HttpStatus.NOT_FOUND.value(), ExceptionMessages.RESOURCE_NOT_FOUND,
            HttpStatus.METHOD_NOT_ALLOWED.value(), ExceptionMessages.METHOD_NOT_ALLOWED,
            HttpStatus.NOT_ACCEPTABLE.value(), ExceptionMessages.NOT_ACCEPTABLE,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), ExceptionMessages.UNSUPPORTED_MEDIA_TYPE
    );

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handlerAuthenticationException(AuthenticationException e) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AuthenticationServiceUnavailableException.class)
    public ResponseEntity<Object> handlerAuthenticationServiceUnavailable(
            AuthenticationServiceUnavailableException e) {
        log.error("Authentication infrastructure failure", e);
        return build(HttpStatus.SERVICE_UNAVAILABLE, ResponseMessages.ERROR);
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Object> handlerTooManyAttempts(TooManyAttemptsException e) {
        log.warn("Login throttled. Retry after {}s", e.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(new ApplicationResponse<>(null, e.getMessage()));
    }

    @ExceptionHandler(CurrentPasswordRequiredException.class)
    public ResponseEntity<Object> handlerCurrentPasswordRequired(
            CurrentPasswordRequiredException e) {
        log.warn(e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(CurrentPasswordInvalidException.class)
    public ResponseEntity<Object> handlerCurrentPasswordInvalid(
            CurrentPasswordInvalidException e) {
        log.warn(e.getMessage());
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Object> handlerForbiddenException(ForbiddenException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(InvalidRoleException.class)
    public ResponseEntity<Object> handlerInvalidRoleException(InvalidRoleException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UserInactiveException.class)
    public ResponseEntity<Object> handlerUserInactiveException(UserInactiveException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UserNotExistException.class)
    public ResponseEntity<Object> handlerUserNotExistException(UserNotExistException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistException.class)
    public ResponseEntity<Object> handlerUserAlreadyExistException(UserAlreadyExistException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AttributeErrorsException.class)
    public ResponseEntity<Object> handlerAttributeErrorsException(AttributeErrorsException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MailSendException.class)
    public ResponseEntity<Object> handlerMailSendException(MailSendException e) {
        log.error("Mail delivery failed, transaction rolled back", e);
        return build(HttpStatus.BAD_GATEWAY, ExceptionMessages.MAIL_SEND);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handlerAccessDeniedException(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, ExceptionMessages.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handlerGenericException(Exception e) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ResponseMessages.ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        if (body instanceof ApplicationResponse){
            return new ResponseEntity<>(body, headers, status);
        }

        if (status.is5xxServerError()){
            log.error("Request failed with status {}", status, ex);
        } else {
            log.warn("Request rejected with status {}: {}", status, ex.getMessage());
        }

        return new ResponseEntity<>(new ApplicationResponse<>(null, resolveMessage(status)),
                headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fieldError -> fieldErrors.put(fieldError.getField(),
                        fieldError.getDefaultMessage()));

        log.warn("Validation failed on {}", ex.getObjectName());

        return new ResponseEntity<>(
                new ApplicationResponse<>(fieldErrors, ExceptionMessages.INVALID_ATTRIBUTES),
                headers, status);
    }

    private String resolveMessage(HttpStatusCode status) {
        return STATUS_MESSAGES.getOrDefault(status.value(), ResponseMessages.ERROR);
    }

    private ResponseEntity<Object> build(HttpStatusCode status, String message) {
        return new ResponseEntity<>(new ApplicationResponse<>(null, message), status);
    }
}
