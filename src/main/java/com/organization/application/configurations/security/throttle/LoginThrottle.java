package com.organization.application.configurations.security.throttle;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limita los intentos de autenticación por cuenta y por dirección de cliente.
 *
 * <p>El estado vive en memoria del proceso: con varias instancias hay que balancear por
 * sesión para que todas compartan el mismo contador, o la protección se multiplica por la
 * cantidad de pods.
 *
 * <p>La dirección de cliente llega desde {@code request.getRemoteAddr()}, que devuelve el proxy
 * y no al usuario cuando la aplicación corre detrás de un reverse proxy. En ese caso
 * {@code app.login.throttle.client-max-failures} deja de proteger a una IP y pasa a bloquear a
 * todos los usuarios a la vez. Ninguna property lo arregla, porque corregirlo del lado de la
 * aplicación implica confiar en el header {@code X-Forwarded-For}, que el cliente controla. Si el
 * despliegue va detrás de un proxy, la corrección corresponde a la infraestructura: el proxy debe
 * resolver la dirección de origen y esta aplicación tomarla desde ahí.
 */
@Component
public class LoginThrottle {

    private final ConcurrentMap<String, Attempt> accountAttempts = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Attempt> clientAttempts = new ConcurrentHashMap<>();

    private final int freeAttempts;

    private final long baseDelayNanos;

    private final long maxDelayNanos;

    private final long resetAfterNanos;

    private final int clientMaxFailures;

    private final long clientWindowNanos;

    private final int maxEntries;

    private final LongSupplier nanoClock;

    @Autowired
    public LoginThrottle(
            @Value("${app.login.throttle.free-attempts:3}") int freeAttempts,
            @Value("${app.login.throttle.base-delay:1000}") long baseDelayMillis,
            @Value("${app.login.throttle.max-delay:30000}") long maxDelayMillis,
            @Value("${app.login.throttle.reset-after:900000}") long resetAfterMillis,
            @Value("${app.login.throttle.client-max-failures:30}") int clientMaxFailures,
            @Value("${app.login.throttle.client-window:300000}") long clientWindowMillis,
            @Value("${app.login.throttle.max-entries:10000}") int maxEntries) {
        this(freeAttempts, baseDelayMillis, maxDelayMillis, resetAfterMillis, clientMaxFailures,
                clientWindowMillis, maxEntries, System::nanoTime);
    }

    LoginThrottle(int freeAttempts, long baseDelayMillis, long maxDelayMillis, long resetAfterMillis,
            int clientMaxFailures, long clientWindowMillis, int maxEntries, LongSupplier nanoClock) {
        this.freeAttempts = freeAttempts;
        this.baseDelayNanos = TimeUnit.MILLISECONDS.toNanos(baseDelayMillis);
        this.maxDelayNanos = TimeUnit.MILLISECONDS.toNanos(maxDelayMillis);
        this.resetAfterNanos = TimeUnit.MILLISECONDS.toNanos(resetAfterMillis);
        this.clientMaxFailures = clientMaxFailures;
        this.clientWindowNanos = TimeUnit.MILLISECONDS.toNanos(clientWindowMillis);
        this.maxEntries = maxEntries;
        this.nanoClock = nanoClock;
    }

    public long retryAfterSeconds(String username, String clientAddress) {
        long now = nanoClock.getAsLong();
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
        long now = nanoClock.getAsLong();
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
        if (baseDelayNanos <= 0 || maxDelayNanos <= 0) {
            return 0;
        }
        int exponent = Math.max(failures - freeAttempts - 1, 0);
        long delay = baseDelayNanos;
        for (int step = 0; step < exponent && delay < maxDelayNanos; step++) {
            delay = delay > (maxDelayNanos >>> 1) ? maxDelayNanos : delay << 1;
        }
        return Math.min(delay, maxDelayNanos);
    }

    private void enforceBound() {
        long now = nanoClock.getAsLong();
        if (accountAttempts.size() > maxEntries) {
            accountAttempts.values().removeIf(attempt ->
                    now - attempt.lastNanos() > resetAfterNanos);
            evictOldestUntilUnderBound(accountAttempts, Attempt::lastNanos);
        }
        if (clientAttempts.size() > maxEntries) {
            clientAttempts.values().removeIf(attempt ->
                    now - attempt.firstNanos() > clientWindowNanos);
            evictOldestUntilUnderBound(clientAttempts, Attempt::firstNanos);
        }
    }

    private void evictOldestUntilUnderBound(ConcurrentMap<String, Attempt> attempts,
            ToLongFunction<Attempt> timestamp) {
        while (attempts.size() > maxEntries) {
            String oldestKey = null;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<String, Attempt> entry : attempts.entrySet()) {
                long value = timestamp.applyAsLong(entry.getValue());
                if (value < oldest) {
                    oldest = value;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                return;
            }
            attempts.remove(oldestKey);
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
