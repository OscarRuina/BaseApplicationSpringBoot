package com.organization.application.configurations.security.throttle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RegistrationThrottle")
class RegistrationThrottleTests {

    private static final String CLIENT = "203.0.113.10";

    private static final String OTHER_CLIENT = "203.0.113.99";

    private static final int MAX_ATTEMPTS = 20;

    private static final long WINDOW_MILLIS = 3600000;

    private static final int MAX_ENTRIES = 10000;

    private long now;

    private final LongSupplier clock = () -> now;

    @Test
    @DisplayName("allows the whole budget and blocks the attempt that exceeds it")
    void allowsTheBudgetAndBlocksTheExtraAttempt() {
        RegistrationThrottle throttle = throttle(MAX_ATTEMPTS, WINDOW_MILLIS, MAX_ENTRIES);

        // El orden importa y es el del servicio: primero se consulta, después se cuenta. Por eso
        // son MAX_ATTEMPTS requests los que pasan y el siguiente se bloquea; si se invirtiera,
        // el presupuesto efectivo sería de MAX_ATTEMPTS - 1.
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            assertEquals(0, throttle.retryAfterSeconds(CLIENT),
                    "attempt " + (attempt + 1) + " of " + MAX_ATTEMPTS
                            + " is inside the budget and must be allowed");
            throttle.recordAttempt(CLIENT);
        }

        assertEquals(WINDOW_MILLIS / 1000, throttle.retryAfterSeconds(CLIENT),
                "the request past the budget must be told how long to wait");
    }

    @Test
    @DisplayName("counts a fresh address against its own budget")
    void countsEachAddressSeparately() {
        RegistrationThrottle throttle = throttle(MAX_ATTEMPTS, WINDOW_MILLIS, MAX_ENTRIES);
        for (int attempt = 0; attempt <= MAX_ATTEMPTS; attempt++) {
            throttle.recordAttempt(CLIENT);
        }

        assertEquals(0, throttle.retryAfterSeconds(OTHER_CLIENT),
                "a shared budget would let one attacker exhaust every other user's registrations");
    }

    @Test
    @DisplayName("forgets the address once the window has elapsed")
    void forgetsTheAddressAfterTheWindow() {
        RegistrationThrottle throttle = throttle(MAX_ATTEMPTS, WINDOW_MILLIS, MAX_ENTRIES);
        for (int attempt = 0; attempt <= MAX_ATTEMPTS; attempt++) {
            throttle.recordAttempt(CLIENT);
        }
        assertTrue(throttle.retryAfterSeconds(CLIENT) > 0);

        now += TimeUnit.MILLISECONDS.toNanos(WINDOW_MILLIS + 1);

        assertEquals(0, throttle.retryAfterSeconds(CLIENT));
    }

    @Test
    @DisplayName("expires the window measured from the first attempt, not the last")
    void expiresTheWindowFromTheFirstAttempt() {
        RegistrationThrottle throttle = throttle(MAX_ATTEMPTS, WINDOW_MILLIS, MAX_ENTRIES);
        throttle.recordAttempt(CLIENT);
        now += TimeUnit.MILLISECONDS.toNanos(WINDOW_MILLIS / 2);
        for (int attempt = 0; attempt <= MAX_ATTEMPTS; attempt++) {
            throttle.recordAttempt(CLIENT);
        }

        // La ventana es fija, no deslizante: los últimos intentos llegan a la mitad de la hora
        // pero no compran otra hora entera. Con una ventana deslizante, un atacante que reparta
        // los 20 envíos a lo largo de la hora nunca dejaría de pasar.
        now += TimeUnit.MILLISECONDS.toNanos(WINDOW_MILLIS / 2 + 1);

        assertEquals(0, throttle.retryAfterSeconds(CLIENT),
                "the budget must be replenished by the clock, not by waiting between attempts");
    }

    @Test
    @DisplayName("reports the remaining seconds rounded up, never zero")
    void reportsTheRemainingSecondsRoundedUp() {
        RegistrationThrottle throttle = throttle(MAX_ATTEMPTS, 10, MAX_ENTRIES);
        for (int attempt = 0; attempt <= MAX_ATTEMPTS; attempt++) {
            throttle.recordAttempt(CLIENT);
        }
        now += TimeUnit.MILLISECONDS.toNanos(9);

        // Queda 1ms de ventana. Un "0" en el Retry-After le dice al cliente que reintente ya, y
        // ese reintento gasta presupuesto: el redondeo hacia arriba es lo que lo frena.
        assertEquals(1, throttle.retryAfterSeconds(CLIENT),
                "a sub-second remainder must round up to 1, not down to 0");
    }

    @Test
    @DisplayName("shares one counter for every unknown address")
    void sharesACounterForUnknownAddresses() {
        RegistrationThrottle throttle = throttle(MAX_ATTEMPTS, WINDOW_MILLIS, MAX_ENTRIES);
        for (int attempt = 0; attempt <= MAX_ATTEMPTS; attempt++) {
            throttle.recordAttempt(null);
        }

        assertTrue(throttle.retryAfterSeconds(null) > 0);
        assertTrue(throttle.retryAfterSeconds(null) > 0,
                "null is one bucket, not a fresh one on every call, otherwise it is free budget");
    }

    @Test
    @DisplayName("evicts old entries so the map cannot grow without bound")
    void evictsOldEntriesUnderPressure() {
        int bound = 20;
        RegistrationThrottle throttle = throttle(MAX_ATTEMPTS, WINDOW_MILLIS, bound);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            throttle.recordAttempt(CLIENT);
        }
        assertTrue(throttle.retryAfterSeconds(CLIENT) > 0);

        for (int entry = 0; entry < 100; entry++) {
            throttle.recordAttempt("filler" + entry);
            now += TimeUnit.MILLISECONDS.toNanos(1);
        }

        // El más antiguo es el primero que se va. Un registro sin tope es un memory leak
        // explotable a propósito: cada entrada es una IP que alguien-controls.
        assertEquals(0, throttle.retryAfterSeconds(CLIENT),
                "the oldest entry must be evicted, otherwise the map grows forever");
    }

    @Test
    @DisplayName("keeps a live blocked client alive while stale addresses free up the map")
    void keepsALiveBlockedClientUnderPressureFromStaleAddresses() {
        int bound = 20;
        RegistrationThrottle throttle = throttle(MAX_ATTEMPTS, WINDOW_MILLIS, bound);
        for (int entry = 0; entry < bound; entry++) {
            throttle.recordAttempt("stale" + entry);
        }

        // Pasada la ventana, esas veinte quedan vencidas y CLIENT arranca la suya propia, viva
        // y ya bloqueada. Al llenarse el mapa de nuevo, la purga de vencidas tiene que bastar
        // para liberar lugar: expulsar a CLIENT sería borrar el bloqueo de un cliente legítimo
        // por culpa del tráfico de otros.
        now += TimeUnit.MILLISECONDS.toNanos(WINDOW_MILLIS + 1);
        for (int attempt = 0; attempt <= MAX_ATTEMPTS; attempt++) {
            throttle.recordAttempt(CLIENT);
        }
        throttle.recordAttempt("one-more-to-trigger-the-bound");

        assertTrue(throttle.retryAfterSeconds(CLIENT) > 0,
                "a live, blocked client must survive pressure coming from other addresses");
        assertEquals(0, throttle.retryAfterSeconds("stale0"),
                "the expired entries are the ones that should have been dropped");
    }

    // Nota sobre cobertura: la purga masiva de `enforceBound` NO tiene test propio. Se verificó
    // por mutación que borrarla deja la suite en verde, y es lo correcto: las entradas vencidas
    // son siempre las de menor `startedNanos`, o sea exactamente las que `evictOldestUntilUnderBound`
    // elegiría de a una. La purga es un atajo de costo (O(n) en vez de O(n^2)), no una política
    // distinta. Un test que "cubriera" esa línea sólo afirmaría algo que el código no garantiza.

    @Test
    @DisplayName("clears every counter so a test does not inherit the previous budget")
    void clearsEveryCounter() {
        RegistrationThrottle throttle = throttle(MAX_ATTEMPTS, WINDOW_MILLIS, MAX_ENTRIES);
        for (int attempt = 0; attempt <= MAX_ATTEMPTS; attempt++) {
            throttle.recordAttempt(CLIENT);
        }

        throttle.clear();

        assertEquals(0, throttle.retryAfterSeconds(CLIENT));
    }

    private RegistrationThrottle throttle(int maxAttempts, long windowMillis, int maxEntries) {
        return new RegistrationThrottle(maxAttempts, windowMillis, maxEntries, clock);
    }
}
