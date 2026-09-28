package com.organization.application.configurations.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.organization.application.configurations.exceptions.MyExceptionHandler;
import com.organization.application.configurations.security.filters.JwtAccessDeniedHandler;
import com.organization.application.configurations.security.filters.SecurityErrorWriter;
import com.organization.application.messages.ExceptionMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class JwtAccessDeniedHandlerTests {

    private static final String INTERNAL_DETAIL = "Access is denied";

    private final JwtAccessDeniedHandler handler =
            new JwtAccessDeniedHandler(new SecurityErrorWriter(new ObjectMapper()));

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private final AccessDeniedException accessDeniedException =
            new AccessDeniedException(INTERNAL_DETAIL);

    @BeforeEach
    void handleDeniedRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        handler.handle(request, response, accessDeniedException);
    }

    @Test
    @DisplayName("Writes a 403 carrying a JSON content type")
    void writesForbiddenAsJson() {
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
    }

    @Test
    @DisplayName("Exposes the curated message and no payload")
    void exposesCuratedMessage() throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        assertEquals(ExceptionMessages.FORBIDDEN, body.get("message").asText());
        assertTrue(body.get("data") == null || body.get("data").isNull());
    }

    @Test
    @DisplayName("Never leaks the exception message")
    void neverLeaksExceptionMessage() throws Exception {
        assertFalse(response.getContentAsString().contains(INTERNAL_DETAIL));
    }

    @Test
    @DisplayName("Returns the same body as the advice for a method level denial")
    void matchesTheAdviceResponse() throws Exception {
        ResponseEntity<Object> fromAdvice = new MyExceptionHandler()
                .handlerAccessDeniedException(accessDeniedException);

        assertEquals(objectMapper.readTree(objectMapper.writeValueAsString(fromAdvice.getBody())),
                objectMapper.readTree(response.getContentAsString()));
    }
}
