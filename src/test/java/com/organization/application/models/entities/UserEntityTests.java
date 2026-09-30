package com.organization.application.models.entities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserEntity")
class UserEntityTests {

    @Test
    @DisplayName("An active user is not pending activation")
    void activeUserIsNotPending() {
        UserEntity user = UserEntity.builder()
                .firstname("Test")
                .lastname("User")
                .email("user@example.com")
                .password("hashed")
                .active(true)
                .activatedAt(timestamp())
                .build();

        assertFalse(user.isPendingActivation(),
                "an account with activatedAt is confirmed, whatever active says today");
    }

    @Test
    @DisplayName("A suspended user is not pending activation")
    void suspendedUserIsNotPending() {
        UserEntity user = UserEntity.builder()
                .firstname("Test")
                .lastname("User")
                .email("user@example.com")
                .password("hashed")
                .active(false)
                .activatedAt(timestamp())
                .build();

        assertFalse(user.isPendingActivation(),
                "suspended means it logged in before and was switched off, which is not the "
                        + "same as never having confirmed");
    }

    @Test
    @DisplayName("A user without activatedAt is pending activation")
    void userWithoutActivationDateIsPending() {
        UserEntity user = UserEntity.builder()
                .firstname("Test")
                .lastname("User")
                .email("user@example.com")
                .password("hashed")
                .active(false)
                .build();

        assertTrue(user.isPendingActivation(),
                "null activatedAt means exactly one thing: registered publicly and unconfirmed");
    }

    private Timestamp timestamp() {
        return new Timestamp(1_700_000_000_000L);
    }
}
