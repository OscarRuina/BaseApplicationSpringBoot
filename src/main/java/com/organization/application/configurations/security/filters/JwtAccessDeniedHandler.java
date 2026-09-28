package com.organization.application.configurations.security.filters;

import com.organization.application.messages.ExceptionMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorWriter securityErrorWriter;

    public JwtAccessDeniedHandler(SecurityErrorWriter securityErrorWriter) {
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        securityErrorWriter.writeError(response,
                HttpServletResponse.SC_FORBIDDEN, ExceptionMessages.FORBIDDEN);

        log.warn("Access denied: {} {}", request.getMethod(), request.getRequestURI());
    }
}
