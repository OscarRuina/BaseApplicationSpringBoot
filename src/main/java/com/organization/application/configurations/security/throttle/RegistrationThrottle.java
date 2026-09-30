package com.organization.application.configurations.security.throttle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limita cuántos registros públicos puede pedir una misma dirección de cliente por hora.
 *
 * <p>No es un límite de seguridad contra fuerza bruta: el registro no autentica a nadie. Es un
 * límite de costo y de abuso, y su otra función es acotar la enumeración de correos. El endpoint
 * devuelve 409 cuando el correo ya existe, así que sin este contador un atacante podría
 * confirmar en bulk cuáles direcciones están registradas en el sistema.
 *
 * <p>Por eso solo cuenta los intentos que llegan al envío del mail, nunca los que caen en el 409
 * por correo duplicado. Si contara también esos, el endpoint se volvería la herramienta perfecta
 * para agotarle el presupuesto a un tercero sin esfuerzo: alcanza con mandar correos que ya
 * existen.
 *
 * <p>El estado vive en memoria del proceso, igual que {@link LoginThrottle}, con las mismas
 * consecuencias: con varias instancias el límite real es el multiplicado por la cantidad de pods,
 * y detrás de un reverse proxy {@code getRemoteAddr()} ve al proxy y no al cliente, con lo cual
 * el límite pasa a bloquear a todos los usuarios a la vez. La corrección, si el despliegue lo
 * requiere, es de infraestructura.
 */
@Component
public class RegistrationThrottle {

    private final ConcurrentMap<String, Window> attempts = new ConcurrentHashMap<>();

    private final int maxAttempts;

    private final long windowNanos;

    private final int maxEntries;

    private final LongSupplier nanoClock;

    @Autowired
    public RegistrationThrottle(
            @Value("${app.registration.throttle.max-attempts:20}") int maxAttempts,
            @Value("${app.registration.throttle.window:3600000}") long windowMillis,
            @Value("${app.registration.throttle.max-entries:10000}") int maxEntries) {
        this(maxAttempts, windowMillis, maxEntries, System::nanoTime);
    }

    RegistrationThrottle(int maxAttempts, long windowMillis, int maxEntries, LongSupplier nanoClock) {
        this.maxAttempts = maxAttempts;
        this.windowNanos = TimeUnit.MILLISECONDS.toNanos(windowMillis);
        this.maxEntries = maxEntries;
        this.nanoClock = nanoClock;
    }

    public long retryAfterSeconds(String clientAddress) {
        long now = nanoClock.getAsLong();
        Window window = attempts.get(clientKey(clientAddress));
        if (window == null || now - window.startedNanos() >= windowNanos) {
            return 0;
        }
        if (window.attempts() < maxAttempts) {
            return 0;
        }
        long remaining = window.startedNanos() + windowNanos - now;
        long second = TimeUnit.SECONDS.toNanos(1);
        return (remaining + second - 1) / second;
    }

    public void recordAttempt(String clientAddress) {
        long now = nanoClock.getAsLong();
        enforceBound(now);
        attempts.compute(clientKey(clientAddress), (key, window) ->
                window == null || now - window.startedNanos() >= windowNanos
                        ? new Window(1, now)
                        : new Window(window.attempts() + 1, window.startedNanos()));
    }

    /**
     * Vacía los contadores. Existe para que los tests no hereden presupuesto del caso anterior,
     * igual que en {@link LoginThrottle}.
     */
    public void clear() {
        attempts.clear();
    }

    private void enforceBound(long now) {
        if (attempts.size() <= maxEntries) {
            return;
        }
        attempts.values().removeIf(window -> now - window.startedNanos() >= windowNanos);
        evictOldestUntilUnderBound(now);
    }

    private void evictOldestUntilUnderBound(long now) {
        while (attempts.size() > maxEntries) {
            String oldestKey = null;
            Window oldestWindow = null;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<String, Window> entry : attempts.entrySet()) {
                if (entry.getValue().startedNanos() < oldest) {
                    oldest = entry.getValue().startedNanos();
                    oldestKey = entry.getKey();
                    oldestWindow = entry.getValue();
                }
            }
            if (oldestKey == null) {
                return;
            }
            attempts.remove(oldestKey, oldestWindow);
        }
    }

    private String clientKey(String clientAddress) {
        return clientAddress == null ? "unknown" : clientAddress;
    }

    private record Window(int attempts, long startedNanos) {
    }
}