package com.organization.application.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.organization.application.AbstractSecuredIntegrationTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class UserControllerAuthorizationTests extends AbstractSecuredIntegrationTest {

    private static final String REGISTER_BODY =
            """
            {"firstname":"Test","lastname":"User","email":"new.user@example.com","role":"USER"}""";

    private static final String STATUS_BODY = """
            {"active": true}""";

    private static final String UPDATE_BODY = """
            {"firstname":"Renamed","lastname":"User"}""";

    private record Endpoint(String path, HttpMethod method, String body, int expected) {
    }

    private static Stream<Endpoint> allEndpoints() {
        return Stream.of(
                new Endpoint("/users/me", HttpMethod.GET, null, 200),
                new Endpoint("/users", HttpMethod.GET, null, 200),
                new Endpoint("/users/register", HttpMethod.POST, REGISTER_BODY, 201),
                new Endpoint("/users/active", HttpMethod.GET, null, 200),
                new Endpoint("/users/{target}", HttpMethod.GET, null, 200),
                new Endpoint("/users/{target}", HttpMethod.DELETE, null, 200),
                new Endpoint("/users/status/{target}", HttpMethod.PUT, STATUS_BODY, 200),
                new Endpoint("/users/roles/{target}?role=ADMIN", HttpMethod.PUT, null, 200),
                new Endpoint("/users", HttpMethod.PUT, UPDATE_BODY, 200));
    }

    private static Stream<Endpoint> adminOnlyEndpoints() {
        return Stream.of(
                new Endpoint("/users", HttpMethod.GET, null, 200),
                new Endpoint("/users/register", HttpMethod.POST, REGISTER_BODY, 201),
                new Endpoint("/users/active", HttpMethod.GET, null, 200),
                new Endpoint("/users/{target}", HttpMethod.GET, null, 200),
                new Endpoint("/users/{target}", HttpMethod.DELETE, null, 200),
                new Endpoint("/users/status/{target}", HttpMethod.PUT, STATUS_BODY, 200),
                new Endpoint("/users/roles/{target}?role=ADMIN", HttpMethod.PUT, null, 200));
    }

    private static Stream<Endpoint> sharedEndpoints() {
        return Stream.of(
                new Endpoint("/users/me", HttpMethod.GET, null, 200),
                new Endpoint("/users", HttpMethod.PUT, UPDATE_BODY, 200));
    }

    private ResultActions call(Endpoint endpoint, String token) throws Exception {
        String path = endpoint.path().replace("{target}", targetId());
        MockHttpServletRequestBuilder builder = request(endpoint.method(), path);
        if (endpoint.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, token);
        }
        return mockMvc.perform(builder);
    }

    @ParameterizedTest(name = "{0} {1} rejects anonymous access")
    @MethodSource("allEndpoints")
    @DisplayName("Every endpoint rejects anonymous access with 401")
    void anonymousAccessIsRejected(Endpoint endpoint) throws Exception {
        call(endpoint, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @ParameterizedTest(name = "{0} {1} rejects a malformed token")
    @MethodSource("allEndpoints")
    @DisplayName("Every endpoint rejects a malformed token with 401")
    void malformedTokenIsRejected(Endpoint endpoint) throws Exception {
        call(endpoint, "Bearer not.a.real.token")
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1} rejects an inactive caller")
    @MethodSource("allEndpoints")
    @DisplayName("A token belonging to a deactivated user is rejected with 401")
    void inactiveCallerIsRejected(Endpoint endpoint) throws Exception {
        call(endpoint, inactiveToken())
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1} rejects a regular user")
    @MethodSource("adminOnlyEndpoints")
    @DisplayName("A regular user cannot reach administrator-only endpoints")
    void regularUserCannotReachAdminEndpoints(Endpoint endpoint) throws Exception {
        call(endpoint, userToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @ParameterizedTest(name = "{0} {1} admits an administrator")
    @MethodSource("allEndpoints")
    @DisplayName("An administrator reaches every endpoint")
    void administratorReachesEveryEndpoint(Endpoint endpoint) throws Exception {
        call(endpoint, adminToken())
                .andExpect(status().is(endpoint.expected()));
    }

    @ParameterizedTest(name = "{0} {1} admits a regular user")
    @MethodSource("sharedEndpoints")
    @DisplayName("A regular user reaches the endpoints shared with administrators")
    void regularUserReachesSharedEndpoints(Endpoint endpoint) throws Exception {
        call(endpoint, userToken())
                .andExpect(status().is(endpoint.expected()));
    }
}
