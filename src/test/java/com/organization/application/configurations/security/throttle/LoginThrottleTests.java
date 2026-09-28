package com.organization.application.configurations.security.throttle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoginThrottle")
class LoginThrottleTests {

    private static final String CLIENT = "203.0.113.10";
    private static final String OTHER_CLIENT = "203.0.113.99";

    private static final int FREE_ATTEMPTS = 3;

    private static final long BASE_DELAY_MILLIS = 1000;

    private static final long MAX_DELAY_MILLIS = 30000;

    private static final long RESET_AFTER_MILLIS = 900000;

    private static final int CLIENT_MAX_FAILURES = 30;

    private static final long CLIENT_WINDOW_MILLIS = 300000;

    private static final int MAX_ENTRIES = 10000;

    private long now;

    private final LongSupplier clock = () -> now;

    @Test
    @DisplayName("allows the free attempts before backing off")
    void allowsTheFreeAttempts() {
        LoginThrottle throttle = throttle(FREE_ATTEMPTS, BASE_DELAY_MILLIS, MAX_DELAY_MILLIS,
                RESET_AFTER_MILLIS, CLIENT_MAX_FAILURES, CLIENT_WINDOW_MILLIS, MAX_ENTRIES);

        for (int attempt = 0; attempt < FREE_ATTEMPTS; attempt++) {
            throttle.recordFailure("user@example.com", CLIENT);
        }

        assertEquals(0, throttle.retryAfterSeconds("user@example.com", CLIENT));
    }

    @Test
    @DisplayName("doubles the delay with every failure past the free attempts")
    void backsOffExponentially() {
        LoginThrottle throttle = throttle(FREE_ATTEMPTS, BASE_DELAY_MILLIS, MAX_DELAY_MILLIS,
                RESET_AFTER_MILLIS, CLIENT_MAX_FAILURES, CLIENT_WINDOW_MILLIS, MAX_ENTRIES);

        throttle.recordFailure("user@example.com", CLIENT);
        throttle.recordFailure("user@example.com", CLIENT);
        throttle.recordFailure("user@example.com", CLIENT);
        assertEquals(0, throttle.retryAfterSeconds("user@example.com", OTHER_CLIENT),
                "the free attempts are not throttled");

        throttle.recordFailure("user@example.com", CLIENT);
        assertEquals(1, throttle.retryAfterSeconds("user@example.com", OTHER_CLIENT));

        throttle.recordFailure("user@example.com", CLIENT);
        assertEquals(2, throttle.retryAfterSeconds("user@example.com", OTHER_CLIENT));

        throttle.recordFailure("user@example.com", CLIENT);
        assertEquals(4, throttle.retryAfterSeconds("user@example.com", OTHER_CLIENT));
    }

    @Test
    @DisplayName("never waits longer than the configured maximum delay")
    void capsTheBackoffAtTheMaximum() {
        LoginThrottle throttle = throttle(FREE_ATTEMPTS, BASE_DELAY_MILLIS, MAX_DELAY_MILLIS,
                RESET_AFTER_MILLIS, CLIENT_MAX_FAILURES, CLIENT_WINDOW_MILLIS, MAX_ENTRIES);

        for (int attempt = 0; attempt < 40; attempt++) {
            throttle.recordFailure("user@example.com", CLIENT);
        }

        assertEquals(MAX_DELAY_MILLIS / 1000,
                throttle.retryAfterSeconds("user@example.com", OTHER_CLIENT));
    }

    @Test
    @DisplayName("keeps throttling when the base delay would overflow a long on shift")
    void keepsThrottlingWhenTheBaseDelayIsLarge() {
        LoginThrottle throttle = throttle(FREE_ATTEMPTS, 10000, MAX_DELAY_MILLIS,
                RESET_AFTER_MILLIS, CLIENT_MAX_FAILURES, CLIENT_WINDOW_MILLIS, MAX_ENTRIES);

        for (int attempt = 0; attempt < 40; attempt++) {
            throttle.recordFailure("user@example.com", CLIENT);
        }

        assertEquals(MAX_DELAY_MILLIS / 1000,
                throttle.retryAfterSeconds("user@example.com", OTHER_CLIENT),
                "a base delay of 10s must not overflow the exponential growth into no throttle");
    }

    @Test
    @DisplayName("forgets the account once the reset window has elapsed")
    void forgetsTheAccountAfterTheResetWindow() {
        LoginThrottle throttle = throttle(FREE_ATTEMPTS, BASE_DELAY_MILLIS, MAX_DELAY_MILLIS,
                RESET_AFTER_MILLIS, CLIENT_MAX_FAILURES, CLIENT_WINDOW_MILLIS, MAX_ENTRIES);
        failAccount(throttle, "user@example.com", FREE_ATTEMPTS + 1);
        assertTrue(throttle.retryAfterSeconds("user@example.com", OTHER_CLIENT) > 0);

        now += TimeUnit.MILLISECONDS.toNanos(RESET_AFTER_MILLIS + 1);

        assertEquals(0, throttle.retryAfterSeconds("user@example.com", OTHER_CLIENT));
    }

    @Test
    @DisplayName("blocks a client once its failures reach the ceiling, even across accounts")
    void blocksTheClientAcrossAccounts() {
        LoginThrottle throttle = throttle(FREE_ATTEMPTS, BASE_DELAY_MILLIS, MAX_DELAY_MILLIS,
                RESET_AFTER_MILLIS, CLIENT_MAX_FAILURES, CLIENT_WINDOW_MILLIS, MAX_ENTRIES);

        for (int attempt = 0; attempt < CLIENT_MAX_FAILURES; attempt++) {
            throttle.recordFailure("attacked" + attempt + "@example.com", CLIENT);
        }

        assertEquals(CLIENT_WINDOW_MILLIS / 1000,
                throttle.retryAfterSeconds("never-seen@example.com", CLIENT));
    }

    @Test
    @DisplayName("does not block a client that stays under the failure ceiling")
    void doesNotBlockAClientUnderTheCeiling() {
        LoginThrottle throttle = throttle(FREE_ATTEMPTS, BASE_DELAY_MILLIS, MAX_DELAY_MILLIS,
                RESET_AFTER_MILLIS, CLIENT_MAX_FAILURES, CLIENT_WINDOW_MILLIS, MAX_ENTRIES);

        for (int attempt = 0; attempt < CLIENT_MAX_FAILURES - 1; attempt++) {
            throttle.recordFailure("attacked" + attempt + "@example.com", CLIENT);
        }

        assertEquals(0, throttle.retryAfterSeconds("never-seen@example.com", CLIENT));
    }

    @Test
    @DisplayName("forgets the client once its window has elapsed")
    void forgetsTheClientAfterItsWindow() {
        LoginThrottle throttle = throttle(FREE_ATTEMPTS, BASE_DELAY_MILLIS, MAX_DELAY_MILLIS,
                RESET_AFTER_MILLIS, CLIENT_MAX_FAILURES, CLIENT_WINDOW_MILLIS, MAX_ENTRIES);
        for (int attempt = 0; attempt < CLIENT_MAX_FAILURES; attempt++) {
            throttle.recordFailure("attacked" + attempt + "@example.com", CLIENT);
        }

        now += TimeUnit.MILLISECONDS.toNanos(CLIENT_WINDOW_MILLIS + 1);

        assertEquals(0, throttle.retryAfterSeconds("never-seen@example.com", CLIENT));
    }

    @Test
    @DisplayName("keeps live accounts blocked when the entry bound is reached")
    void keepsLiveAccountsBlockedWhenTheBoundIsReached() {
        int bound = 20;
        int fillers = 10;
        LoginThrottle throttle = throttle(FREE_ATTEMPTS, BASE_DELAY_MILLIS, MAX_DELAY_MILLIS,
                RESET_AFTER_MILLIS, CLIENT_MAX_FAILURES, CLIENT_WINDOW_MILLIS, bound);
        for (int entry = 0; entry < fillers; entry++) {
            throttle.recordFailure("filler" + entry + "@example.com", CLIENT);
        }

        List<String> victims = List.of("victim0@example.com", "victim1@example.com",
                "victim2@example.com");
        victims.forEach(victim -> failAccount(throttle, victim, FREE_ATTEMPTS + 1));
        for (String victim : victims) {
            assertTrue(throttle.retryAfterSeconds(victim, OTHER_CLIENT) > 0,
                    victim + " starts blocked");
        }

        for (int entry = 0; entry < 12; entry++) {
            throttle.recordFailure("late" + entry + "@example.com", CLIENT);
        }

        for (String victim : victims) {
            assertTrue(throttle.retryAfterSeconds(victim, OTHER_CLIENT) > 0,
                    victim + " must survive the bound: evicting the oldest entries cannot reset"
                            + " every live counter");
        }
    }

    @Test
    @DisplayName("clears the account counter on success but keeps the client counter")
    void clearsTheAccountCounterOnSuccess() {
        LoginThrottle throttle = throttle(FREE_ATTEMPTS, BASE_DELAY_MILLIS, MAX_DELAY_MILLIS,
                RESET_AFTER_MILLIS, CLIENT_MAX_FAILURES, CLIENT_WINDOW_MILLIS, MAX_ENTRIES);
        for (int attempt = 0; attempt < CLIENT_MAX_FAILURES; attempt++) {
            throttle.recordFailure("attacked" + attempt + "@example.com", CLIENT);
        }

        throttle.recordSuccess("attacked0@example.com");

        assertEquals(0, throttle.retryAfterSeconds("attacked0@example.com", OTHER_CLIENT),
                "a successful login clears that account");
        assertTrue(throttle.retryAfterSeconds("attacked0@example.com", CLIENT) > 0,
                "a successful login must not clear the client failures, the address may still be attacking");
    }

    @Test
    @DisplayName("counts usernames regardless of case and surrounding whitespace")
    void normalizesUsernames() {
        LoginThrottle throttle = throttle(FREE_ATTEMPTS, BASE_DELAY_MILLIS, MAX_DELAY_MILLIS,
                RESET_AFTER_MILLIS, CLIENT_MAX_FAILURES, CLIENT_WINDOW_MILLIS, MAX_ENTRIES);
        failAccount(throttle, "User@Example.com  ", FREE_ATTEMPTS + 1);

        assertTrue(throttle.retryAfterSeconds("user@example.com", OTHER_CLIENT) > 0);
    }

    private void failAccount(LoginThrottle throttle, String username, int failures) {
        for (int attempt = 0; attempt < failures; attempt++) {
            throttle.recordFailure(username, CLIENT);
            now += TimeUnit.MILLISECONDS.toNanos(1);
        }
    }

    private LoginThrottle throttle(int freeAttempts, long baseDelayMillis, long maxDelayMillis,
            long resetAfterMillis, int clientMaxFailures, long clientWindowMillis, int maxEntries) {
        return new LoginThrottle(freeAttempts, baseDelayMillis, maxDelayMillis, resetAfterMillis,
                clientMaxFailures, clientWindowMillis, maxEntries, clock);
    }
}
