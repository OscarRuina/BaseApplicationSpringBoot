package com.organization.application.configurations.security.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.organization.application.configurations.exceptions.InvalidTokenException;
import com.organization.application.configurations.security.jwt.JwtUtil;
import com.organization.application.configurations.security.service.UserPrincipal;
import com.organization.application.messages.ResponseMessages;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class JwtFilterTests {

    private static final String TOKEN = "valid-token";

    private final JwtUtil jwtUtil = mock(JwtUtil.class);

    private final org.springframework.security.core.userdetails.UserDetailsService userDetailsService =
            mock(org.springframework.security.core.userdetails.UserDetailsService.class);

    private final JwtFilter filter = new JwtFilter(jwtUtil, userDetailsService,
            new SecurityErrorWriter(new ObjectMapper()));

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private boolean chainProceeded;

    @BeforeEach
    void setUp() {
        chainProceeded = false;
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Proceeds untouched without an Authorization header")
    void proceedsWithoutAuthorizationHeader() throws Exception {
        filter.doFilterInternal(request, response, recordingChain());

        assertTrue(chainProceeded);
        assertEquals(HttpStatus.OK.value(), response.getStatus());
        verifyNoInteractions(jwtUtil);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Authenticates the principal from a valid token")
    void authenticatesFromValidToken() throws Exception {
        when(jwtUtil.getUsername(TOKEN)).thenReturn("admin@example.com");
        when(userDetailsService.loadUserByUsername("admin@example.com")).thenReturn(activePrincipal());

        request.addHeader("Authorization", "Bearer " + TOKEN);
        filter.doFilterInternal(request, response, recordingChain());

        assertTrue(chainProceeded);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("admin@example.com", authentication.getName());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("Continues down the chain after an invalid token, without authenticating")
    void continuesAfterInvalidToken() throws Exception {
        when(jwtUtil.getUsername("broken-token"))
                .thenThrow(new InvalidTokenException("token rejected", new RuntimeException()));

        request.addHeader("Authorization", "Bearer broken-token");
        filter.doFilterInternal(request, response, recordingChain());

        assertTrue(chainProceeded);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Continues after a token references a deleted user, without authenticating")
    void continuesAfterTokenOfDeletedUser() throws Exception {
        when(jwtUtil.getUsername(TOKEN)).thenReturn("ghost@example.com");
        when(userDetailsService.loadUserByUsername("ghost@example.com"))
                .thenThrow(new UsernameNotFoundException("no such user"));

        request.addHeader("Authorization", "Bearer " + TOKEN);
        filter.doFilterInternal(request, response, recordingChain());

        assertTrue(chainProceeded);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Continues after a token of an inactive user, without authenticating")
    void continuesAfterTokenOfInactiveUser() throws Exception {
        when(jwtUtil.getUsername(TOKEN)).thenReturn("inactive@example.com");
        when(userDetailsService.loadUserByUsername("inactive@example.com")).thenReturn(inactivePrincipal());

        request.addHeader("Authorization", "Bearer " + TOKEN);
        filter.doFilterInternal(request, response, recordingChain());

        assertTrue(chainProceeded);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Writes a 503 and stops the chain on an authentication infrastructure failure")
    void stopsTheChainOnInfrastructureFailure() throws Exception {
        when(jwtUtil.getUsername(TOKEN))
                .thenThrow(new AuthenticationServiceException("auth service is down"));

        request.addHeader("Authorization", "Bearer " + TOKEN);
        filter.doFilterInternal(request, response, recordingChain());

        assertFalse(chainProceeded);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(ResponseMessages.ERROR, body.get("message").asText());
        assertFalse(body.has("data"));
    }

    private FilterChain recordingChain() {
        return (servletRequest, servletResponse) -> chainProceeded = true;
    }

    private UserPrincipal activePrincipal() {
        return principal("admin@example.com", true);
    }

    private UserPrincipal inactivePrincipal() {
        return principal("inactive@example.com", false);
    }

    private UserPrincipal principal(String email, boolean active) {
        UserEntity user = UserEntity.builder()
                .email(email)
                .active(active)
                .roleEntities(Set.of(RoleEntity.builder().type(RoleType.ADMIN).build()))
                .build();
        return new UserPrincipal(user);
    }
}