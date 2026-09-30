package com.organization.application.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.organization.application.AbstractSecuredIntegrationTest;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

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
    @DisplayName("A public registration returns 201 with the account inactive and unconfirmed")
    void publicRegistrationCreatesAPendingAccount() throws Exception {
        String created = mockMvc.perform(post(USERS + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname":"Test","lastname":"User","email":"pending@example.com"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.activatedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertFalse(created.contains("activationToken"),
                "the registration response must not hand the caller a usable token: "
                        + created);

        UserEntity stored = userRepository.findByEmail("pending@example.com").orElseThrow();
        assertTrue(stored.isPendingActivation(),
                "a public registration is pending by definition until the mail is followed");
        assertFalse(stored.getActivationToken().isBlank(),
                "the row must carry the token hash so the emailed token can be redeemed");
    }

    @Test
    @DisplayName("A public registration never accepts a role, so privilege cannot be self-assigned")
    void publicRegistrationIgnoresAForgedRole() throws Exception {
        mockMvc.perform(post(USERS + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname":"Root","lastname":"User",
                                "email":"root@example.com","role":"ADMIN"}"""))
                .andExpect(status().isCreated());

        UserEntity stored = userRepository.findByEmail("root@example.com").orElseThrow();
        assertTrue(stored.getRoleEntities().stream()
                        .noneMatch(role -> role.getType() == RoleType.ADMIN),
                "an unknown property must not be honoured: the role is fixed server-side");
    }

    @Test
    @DisplayName("A public registration reissues a token that expired on an unconfirmed account")
    void publicRegistrationReissuesAnExpiredToken() throws Exception {
        UserEntity pending = persistPendingUser("expired@example.com",
                new Timestamp(System.currentTimeMillis() - 1));

        String firstToken = pending.getActivationToken();

        mockMvc.perform(post(USERS + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname":"Pending","lastname":"User",
                                "email":"expired@example.com"}"""))
                .andExpect(status().isCreated());

        UserEntity reloaded = userRepository.findByEmail("expired@example.com").orElseThrow();
        assertNotEquals(firstToken, reloaded.getActivationToken(),
                "the dead token must be rotated, otherwise the user has no way back in");
        assertTrue(reloaded.isPendingActivation(),
                "reissuing the token must not confirm the account on its own");
    }

    @Test
    @DisplayName("Activating with a token nobody holds returns 400")
    void activationWithAnUnknownTokenReturnsBadRequest() throws Exception {
        mockMvc.perform(post(USERS + "/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"a-token-nobody-issued","password":"ValidPassw0rd!"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Activating with an expired token returns 410 and leaves the account pending")
    void activationWithAnExpiredTokenReturnsGone() throws Exception {
        UserEntity pending = persistPendingUser("expired@example.com",
                new Timestamp(System.currentTimeMillis() - 1));

        mockMvc.perform(post(USERS + "/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"ValidPassw0rd!"}"""
                                .formatted(plainTokenFor("expired@example.com"))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertTrue(userRepository.findById(pending.getId()).orElseThrow().isPendingActivation(),
                "an expired token must not confirm the account");
    }

    @Test
    @DisplayName("Reusing a spent token returns 409 instead of pretending it never existed")
    void activationWithASpentTokenReturnsConflict() throws Exception {
        UserEntity pending = persistPendingUser("spent@example.com", futureExpiry());
        String token = plainTokenFor("spent@example.com");

        activate(token);
        activate(token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Activating a pending account sets its password and lets it log in")
    void activationConfirmsTheAccount() throws Exception {
        UserEntity pending = persistPendingUser("fresh@example.com", futureExpiry());
        String token = plainTokenFor("fresh@example.com");

        activate(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.activatedAt").isNotEmpty());

        UserEntity reloaded = userRepository.findById(pending.getId()).orElseThrow();
        assertFalse(reloaded.isPendingActivation(),
                "activatedAt must be set or the API keeps reporting the account as pending");
        assertTrue(passwordEncoder.matches("ValidPassw0rd!", reloaded.getPassword()),
                "the password chosen at activation must be the one stored");
    }

    @Test
    @DisplayName("Activation rejects a password outside the policy with 400")
    void activationRejectsAWeakPassword() throws Exception {
        UserEntity pending = persistPendingUser("weak@example.com", futureExpiry());
        String token = plainTokenFor("weak@example.com");

        activate(token, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.password").exists());

        assertTrue(passwordEncoder.matches("placeholder-placeholder", pending.getPassword()),
                "a rejected password must not be stored");
    }

    @Test
    @DisplayName("A spent token stays spent even if the admin suspends the account afterwards")
    void activationCannotReopenASuspendedAccount() throws Exception {
        UserEntity pending = persistPendingUser("suspended-pending@example.com", futureExpiry());
        String token = plainTokenFor("suspended-pending@example.com");
        activate(token);

        // El admin la suspende y el usuario intenta canjear el mismo token otra vez. El canje
        // se rechaza por token gastado, no por la suspensión: la suspensión tampoco se
        // "deshace" desde acá porque el activate sólo restaura la cuenta que su propio token
        // confirmó, y ese token ya no sirve para volver a confirmar nada.
        mockMvc.perform(put(USERS + "/status/" + pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}"""))
                .andExpect(status().isOk());

        activate(token).andExpect(status().isConflict());

        assertFalse(userRepository.findById(pending.getId()).orElseThrow().isActive(),
                "a spent token must not reopen the account the admin just suspended");
    }

    @Test
    @DisplayName("The registration throttle answers 429 with Retry-After")
    void registrationThrottleAnswersTooManyRequests() throws Exception {
        // La primera llamada deja la fila creada, así que el 409 no cuenta: hace falta llegar
        // al envío del mail para consumir presupuesto.
        for (int attempt = 0; attempt < 20; attempt++) {
            mockMvc.perform(post(USERS + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"firstname":"Spam","lastname":"Bot","email":"spam-%d@example.com"}"""
                                    .formatted(attempt)))
                    .andExpect(status().isCreated());
        }

        MvcResult throttled = mockMvc.perform(post(USERS + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname":"Spam","lastname":"Bot","email":"one-too-many@example.com"}"""))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn();

        // Un cliente bienintencionado necesita saber cuándo reintentar; sin el header sólo
        // puede probar cada segundo y se vuelve a consumir cuota. El valor son los segundos
        // enteros que quedan de la ventana, así que va de 1 a la hora: no se fija en 3600
        // porque el primer intento ya arrancó la ventana unos milisegundos antes.
        String retryAfter = throttled.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertNotNull(retryAfter, "429 must tell the client when to come back");
        long seconds = Long.parseLong(retryAfter);
        assertTrue(seconds > 0 && seconds <= 3600,
                "Retry-After must be the remaining window in seconds, got " + retryAfter);
    }

    @Test
    @DisplayName("Registration rejects a duplicated email with 409")
    void registrationRejectsDuplicatedEmail() throws Exception {
        String payload = """
                {"firstname":"Test","lastname":"User","email":"%s"}""".formatted(USER_EMAIL);

        mockMvc.perform(post(USERS + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A response never carries the activation token")
    void responseNeverExposesTheActivationToken() throws Exception {
        String body = mockMvc.perform(get(USERS)
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("activationToken"),
                "the token column must never reach the JSON contract: " + body);
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
    @DisplayName("Update status on an account that never confirmed its registration reports 409")
    void updateStatusOnPendingAccountReportsConflict() throws Exception {
        UserEntity pending = persistPendingUser();

        mockMvc.perform(put(USERS + "/status/" + pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": true}"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Suspending a pending account reports 409 instead of a silent no-op")
    void suspendingAPendingAccountReportsConflict() throws Exception {
        UserEntity pending = persistPendingUser();

        // La cuenta ya está inactiva, así que sin el guard esto devolvería un 200 y el admin
        // creería que suspendió un registro sin confirmar.
        mockMvc.perform(put(USERS + "/status/" + pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}"""))
                .andExpect(status().isConflict());
    }

    private static final String TOKEN_PREFIX = "token-for-";

    private UserEntity persistPendingUser(String email, Timestamp activationExpiresAt) {
        Set<RoleEntity> roles = new HashSet<>();
        roles.add(roleRepository.findByType(RoleType.USER).orElseThrow());
        return userRepository.save(UserEntity.builder()
                .firstname("Pending")
                .lastname("User")
                .email(email)
                .password(passwordEncoder.encode("placeholder-placeholder"))
                .active(false)
                .activatedAt(null)
                .activationToken(sha256(TOKEN_PREFIX + email))
                .activationExpiresAt(activationExpiresAt)
                .roleEntities(roles)
                .build());
    }

    private UserEntity persistPendingUser() {
        return persistPendingUser("pending@example.com", futureExpiry());
    }

    private static Timestamp futureExpiry() {
        return new Timestamp(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000);
    }

    /**
     * La base guarda el SHA-256 del token, y el hash no se puede deshacer. La fixture fabrica
     * una entrada fija y derivable en vez de capturar el correo: el objetivo es ejercitar el
     * endpoint, no el servidor SMTP.
     */
    private static String plainTokenFor(String email) {
        return TOKEN_PREFIX + email;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions activate(String token) throws Exception {
        return activate(token, "ValidPassw0rd!");
    }

    private ResultActions activate(String token, String password) throws Exception {
        return mockMvc.perform(post(USERS + "/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","password":"%s"}""".formatted(token, password)));
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

    private String userIdOf(String email) {
        return String.valueOf(userRepository.findByEmail(email).orElseThrow().getId());
    }
}
