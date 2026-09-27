package com.organization.application;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractIntegrationTest {

    private static final int JWT_KEY_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @DynamicPropertySource
    static void jwtSecretKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[JWT_KEY_BYTES];
        SECURE_RANDOM.nextBytes(key);
        registry.add("jwt.token.secretKey",
                () -> Base64.getEncoder().encodeToString(key));
    }
}
