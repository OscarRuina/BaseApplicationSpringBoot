package com.organization.application.configurations.security.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.organization.application.messages.ExceptionMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class JwtEntryPointTests {

    private static final String INTERNAL_DETAIL = "vault lookup failed for acme key";

    private final JwtEntryPoint entryPoint =
            new JwtEntryPoint(new SecurityErrorWriter(new ObjectMapper()));

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private final BadCredentialsException authException =
            new BadCredentialsException(INTERNAL_DETAIL);

    @BeforeEach
    void startAuthenticationFailure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        entryPoint.commence(request, response, authException);
    }

    @Test
    @DisplayName("Writes a 401 carrying a JSON content type")
    void writesUnauthorizedAsJson() {
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
    }

    @Test
    @DisplayName("Exposes the curated message and no payload")
    void exposesCuratedMessageWithoutData() throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        assertEquals(ExceptionMessages.UNAUTHORIZED, body.get("message").asText());
        assertFalse(body.has("data"));
    }

    @Test
    @DisplayName("Never leaks the authentication exception detail")
    void neverLeaksAuthenticationExceptionDetail() throws Exception {
        assertFalse(response.getContentAsString().contains(INTERNAL_DETAIL));
    }
}