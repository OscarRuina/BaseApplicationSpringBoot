package com.organization.application.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.organization.application.AbstractSecuredIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class UserControllerContractTests extends AbstractSecuredIntegrationTest {

    private static final String USERS = "/users";

    private static final String VALID_PROFILE = """
            {"firstname":"Renamed","lastname":"User"}""";

    @Test
    @DisplayName("Registration rejects an invalid payload with per-field details")
    void registrationRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post(USERS + "/register")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname":"","lastname":"User","email":"not-an-email","role":"USER"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.firstname").exists())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Registration rejects a privileged role with 400")
    void registrationRejectsPrivilegedRole() throws Exception {
        mockMvc.perform(post(USERS + "/register")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname":"Test","lastname":"User","email":"root@example.com","role":"ADMIN"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Registration rejects a duplicated email with 409")
    void registrationRejectsDuplicatedEmail() throws Exception {
        String payload = "{\"firstname\":\"Test\",\"lastname\":\"User\",\"email\":\""
                + USER_EMAIL + "\",\"role\":\"USER\"}";

        mockMvc.perform(post(USERS + "/register")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Looking up an unknown id returns 404")
    void unknownIdReturnsNotFound() throws Exception {
        mockMvc.perform(get(USERS + "/9999")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("A non numeric id is rejected with 400")
    void nonNumericIdIsRejected() throws Exception {
        mockMvc.perform(get(USERS + "/not-a-number")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Update status requires the active flag")
    void updateStatusRequiresTheActiveFlag() throws Exception {
        mockMvc.perform(put(USERS + "/status/2")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.active").exists());
    }

    @Test
    @DisplayName("Update status rejects a null active flag")
    void updateStatusRejectsNullActiveFlag() throws Exception {
        mockMvc.perform(put(USERS + "/status/2")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": null}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Update status reactivates a deactivated user")
    void updateStatusReactivatesADeactivatedUser() throws Exception {
        mockMvc.perform(put(USERS + "/status/" + inactiveUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("Update status deactivates an active user")
    void updateStatusDeactivatesAnActiveUser() throws Exception {
        mockMvc.perform(put(USERS + "/status/" + regularUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("An administrator cannot change their own status")
    void administratorCannotChangeOwnStatus() throws Exception {
        mockMvc.perform(put(USERS + "/status/" + admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Delete refuses to remove an administrator")
    void deleteRefusesAdministrators() throws Exception {
        mockMvc.perform(delete(USERS + "/" + admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Delete on a deactivated user reports 409")
    void deleteOnDeactivatedUserReportsConflict() throws Exception {
        mockMvc.perform(delete(USERS + "/" + inactiveUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Update role on a deactivated user reports 409")
    void updateRoleOnDeactivatedUserReportsConflict() throws Exception {
        mockMvc.perform(put(USERS + "/roles/" + inactiveUser.getId()).param("role", "ADMIN")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Update role with an unknown role is rejected with 400")
    void updateRoleWithUnknownRoleIsRejected() throws Exception {
        mockMvc.perform(put(USERS + "/roles/" + regularUser.getId()).param("role", "SUPERUSER")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Changing the password without the current one reports 400")
    void changingPasswordWithoutCurrentReportsBadRequest() throws Exception {
        mockMvc.perform(put(USERS)
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname":"Test","lastname":"User","password":"BrandNewPass1"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Changing the password with a wrong current one reports 403")
    void changingPasswordWithWrongCurrentReportsForbidden() throws Exception {
        mockMvc.perform(put(USERS)
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname":"Test","lastname":"User","password":"BrandNewPass1","currentPassword":"WrongOne1"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Updating the profile without a password change is accepted")
    void profileUpdateWithoutPasswordIsAccepted() throws Exception {
        mockMvc.perform(put(USERS)
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PROFILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstname").value("Renamed"));
    }

    @Test
    @DisplayName("Error bodies never expose framework internals")
    void errorBodiesDoNotLeakInternals() throws Exception {
        String notFound = mockMvc.perform(get(USERS + "/9999")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andReturn().getResponse().getContentAsString();

        String forbidden = mockMvc.perform(delete(USERS + "/" + admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andReturn().getResponse().getContentAsString();

        String conflict = mockMvc.perform(delete(USERS + "/" + inactiveUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andReturn().getResponse().getContentAsString();

        String[] bodies = {notFound, forbidden, conflict};
        for (String body : bodies) {
            assertFalse(body.contains("org.springframework"),
                    "a Spring class name leaked: " + body);
            assertFalse(body.contains("org.hibernate"),
                    "a Hibernate class name leaked: " + body);
            assertFalse(body.contains("Exception"),
                    "an exception class name leaked: " + body);
            assertFalse(body.contains("jakarta."),
                    "a Jakarta type leaked: " + body);
        }
    }
}
