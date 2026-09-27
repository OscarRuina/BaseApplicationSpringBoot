package com.organization.application.controllers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.organization.application.AbstractSecuredIntegrationTest;
import com.organization.application.dtos.request.LoginRequestDTO;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetailsService;

class SecurityControllerTests extends AbstractSecuredIntegrationTest {

    private static final String LOGIN_URL = "/auth/login";
    private static final String RETRY_AFTER = HttpHeaders.RETRY_AFTER;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SpyBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void restoreUserDetailsService() {
        Mockito.reset(userDetailsService);
    }

    private String body(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginRequestDTO(username, password));
    }

    @Test
    @DisplayName("Valid credentials return a bearer token")
    void validCredentialsReturnAToken() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(USER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(USER_EMAIL))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("A wrong password is rejected with 401")
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(USER_EMAIL, "WrongPassw0rd!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("An unknown account is rejected with the same 401 contract")
    void unknownAccountIsRejected() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ABSENT_EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An inactive account cannot log in")
    void inactiveAccountIsRejected() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(INACTIVE_EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An empty payload is rejected with 400 and per-field details")
    void emptyPayloadIsRejected() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.username").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    @DisplayName("A throttled caller receives 429 with the retry delay")
    void throttledCallerReceivesRetryAfter() throws Exception {
        when(loginThrottle.retryAfterSeconds(anyString(), anyString())).thenReturn(42L);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(USER_EMAIL, PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(RETRY_AFTER, "42"));
    }

    @Test
    @DisplayName("An infrastructure failure surfaces as 503 without leaking internals")
    void infrastructureFailureSurfacesAs503() throws Exception {
        doThrow(new AuthenticationServiceException("identity provider unreachable"))
                .when(userDetailsService).loadUserByUsername(anyString());

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(USER_EMAIL, PASSWORD)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value(
                        Matchers.not(Matchers.containsString("identity provider"))));
    }
}
