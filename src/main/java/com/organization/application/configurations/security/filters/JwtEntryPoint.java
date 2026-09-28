package com.organization.application.configurations.security.filters;

import com.organization.application.messages.ExceptionMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter securityErrorWriter;

    public JwtEntryPoint(SecurityErrorWriter securityErrorWriter) {
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        securityErrorWriter.writeError(response,
                HttpServletResponse.SC_UNAUTHORIZED, ExceptionMessages.UNAUTHORIZED);

        log.warn("Authentication error: {}", authException.getMessage(), authException);
    }
}
