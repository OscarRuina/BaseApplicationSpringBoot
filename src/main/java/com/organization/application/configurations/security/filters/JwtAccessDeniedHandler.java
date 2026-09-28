package com.organization.application.configurations.security.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.organization.application.dtos.response.ApplicationResponse;
import com.organization.application.messages.ExceptionMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ApplicationResponse<Object> applicationResponse =
                new ApplicationResponse<>(null, ExceptionMessages.FORBIDDEN);

        ObjectMapper mapper = new ObjectMapper();
        String jsonResponse = mapper.writeValueAsString(applicationResponse);

        response.getWriter().write(jsonResponse);

        log.warn("Access denied: {} {}", request.getMethod(), request.getRequestURI());
    }
}
