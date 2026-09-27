package com.organization.application.configurations.security.throttle;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginThrottle {

    private static final int MAX_BACKOFF_EXPONENT = 30;

    private final ConcurrentMap<String, Attempt> accountAttempts = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Attempt> clientAttempts = new ConcurrentHashMap<>();

    private final int freeAttempts;

    private final long baseDelayNanos;

    private final long maxDelayNanos;

    private final long resetAfterNanos;

    private final int clientMaxFailures;

    private final long clientWindowNanos;

    private final int maxEntries;

    public LoginThrottle(
            @Value("${app.login.throttle.free-attempts:3}") int freeAttempts,
            @Value("${app.login.throttle.base-delay:1000}") long baseDelayMillis,
            @Value("${app.login.throttle.max-delay:30000}") long maxDelayMillis,
            @Value("${app.login.throttle.reset-after:900000}") long resetAfterMillis,
            @Value("${app.login.throttle.client-max-failures:30}") int clientMaxFailures,
            @Value("${app.login.throttle.client-window:300000}") long clientWindowMillis,
            @Value("${app.login.throttle.max-entries:10000}") int maxEntries) {
        this.freeAttempts = freeAttempts;
        this.baseDelayNanos = TimeUnit.MILLISECONDS.toNanos(baseDelayMillis);
        this.maxDelayNanos = TimeUnit.MILLISECONDS.toNanos(maxDelayMillis);
        this.resetAfterNanos = TimeUnit.MILLISECONDS.toNanos(resetAfterMillis);
        this.clientMaxFailures = clientMaxFailures;
        this.clientWindowNanos = TimeUnit.MILLISECONDS.toNanos(clientWindowMillis);
        this.maxEntries = maxEntries;
    }

    public long retryAfterSeconds(String username, String clientAddress) {
        long now = System.nanoTime();
        long blockedUntil = Math.max(accountBlockedUntil(normalize(username), now),
                clientBlockedUntil(clientKey(clientAddress), now));
        long remaining = blockedUntil - now;
        if (remaining <= 0) {
            return 0;
        }
        long second = TimeUnit.SECONDS.toNanos(1);
        return (remaining + second - 1) / second;
    }

    public void recordFailure(String username, String clientAddress) {
        long now = System.nanoTime();
        enforceBound();
        accountAttempts.compute(normalize(username), (key, attempt) ->
                nextAccountAttempt(attempt, now));
        clientAttempts.compute(clientKey(clientAddress), (key, attempt) ->
                nextClientAttempt(attempt, now));
    }

    public void recordSuccess(String username) {
        accountAttempts.remove(normalize(username));
    }

    private Attempt nextAccountAttempt(Attempt attempt, long now) {
        if (attempt == null || now - attempt.lastNanos() > resetAfterNanos) {
            return new Attempt(1, now, now);
        }
        return new Attempt(attempt.failures() + 1, attempt.firstNanos(), now);
    }

    private Attempt nextClientAttempt(Attempt attempt, long now) {
        if (attempt == null || now - attempt.firstNanos() > clientWindowNanos) {
            return new Attempt(1, now, now);
        }
        return new Attempt(attempt.failures() + 1, attempt.firstNanos(), now);
    }

    private long accountBlockedUntil(String key, long now) {
        Attempt attempt = accountAttempts.get(key);
        if (attempt == null || now - attempt.lastNanos() > resetAfterNanos) {
            return 0;
        }
        if (attempt.failures() <= freeAttempts) {
            return 0;
        }
        return attempt.lastNanos() + backoffNanos(attempt.failures());
    }

    private long clientBlockedUntil(String key, long now) {
        Attempt attempt = clientAttempts.get(key);
        if (attempt == null || now - attempt.firstNanos() > clientWindowNanos) {
            return 0;
        }
        if (attempt.failures() < clientMaxFailures) {
            return 0;
        }
        return attempt.firstNanos() + clientWindowNanos;
    }

    private long backoffNanos(int failures) {
        int exponent = Math.min(failures - freeAttempts - 1, MAX_BACKOFF_EXPONENT);
        return Math.min(baseDelayNanos << exponent, maxDelayNanos);
    }

    private void enforceBound() {
        long now = System.nanoTime();
        if (accountAttempts.size() > maxEntries) {
            accountAttempts.values().removeIf(attempt ->
                    now - attempt.lastNanos() > resetAfterNanos);
            if (accountAttempts.size() > maxEntries) {
                accountAttempts.clear();
            }
        }
        if (clientAttempts.size() > maxEntries) {
            clientAttempts.values().removeIf(attempt ->
                    now - attempt.firstNanos() > clientWindowNanos);
            if (clientAttempts.size() > maxEntries) {
                clientAttempts.clear();
            }
        }
    }

    private String normalize(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String clientKey(String clientAddress) {
        return clientAddress == null ? "unknown" : clientAddress;
    }

    private record Attempt(int failures, long firstNanos, long lastNanos) {
    }
}
