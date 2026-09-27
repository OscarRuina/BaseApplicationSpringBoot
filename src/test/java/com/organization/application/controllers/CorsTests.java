package com.organization.application.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.organization.application.AbstractSecuredIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorsTests extends AbstractSecuredIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final String FOREIGN_ORIGIN = "http://attacker.example.com";
    private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String REQUEST_METHOD = "Access-Control-Request-Method";
    private static final String REQUEST_HEADERS = "Access-Control-Request-Headers";

    @Test
    @DisplayName("A preflight from the configured origin is answered before authorization")
    void preflightFromAllowedOriginIsAnswered() throws Exception {
        mockMvc.perform(options("/users")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header(REQUEST_METHOD, "PUT")
                        .header(REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().exists(ALLOW_METHODS))
                .andExpect(header().exists(ALLOW_HEADERS));
    }

    @Test
    @DisplayName("A preflight from an unknown origin is rejected")
    void preflightFromForeignOriginIsRejected() throws Exception {
        mockMvc.perform(options("/users")
                        .header("Origin", FOREIGN_ORIGIN)
                        .header(REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("An actual request from the configured origin carries the allow-origin header")
    void actualRequestFromAllowedOriginIsDecorated() throws Exception {
        mockMvc.perform(get("/users")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    @DisplayName("An actual request from an unknown origin is rejected by the cors filter")
    void actualRequestFromForeignOriginIsRejected() throws Exception {
        mockMvc.perform(get("/users")
                        .header("Origin", FOREIGN_ORIGIN)
                        .header("Authorization", adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(ALLOW_ORIGIN));
    }
}
