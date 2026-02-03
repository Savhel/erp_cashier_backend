package com.erp.cashier.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.erp.cashier.dto.LoginUserResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "test-secret-32-characters-long-1234";

    @Test
    void generatesAndParsesTokens() {
        JwtService jwtService = new JwtService(SECRET, "erp-cashier", 15);
        LoginUserResponse user = new LoginUserResponse(
                "user-123",
                "demo",
                "admin",
                "superadmin",
                "agency-1",
                "org-1"
        );

        String token = jwtService.generateAccessToken(user);
        JwtPayload payload = jwtService.parseToken(token);

        assertEquals("user-123", payload.getUserId());
        assertEquals("demo", payload.getUsername());
        assertEquals("admin", payload.getRole());
        assertEquals("superadmin", payload.getRoleType());
        assertEquals("agency-1", payload.getAgencyId());
        assertEquals("org-1", payload.getOrganizationId());
        assertNotNull(payload.getExpiresAt());
        assertTrue(payload.getExpiresAt().isAfter(Instant.now().minusSeconds(5)));
    }

    @Test
    void rejectsShortSecrets() {
        assertThrows(IllegalStateException.class, () -> new JwtService("short", "issuer", 60));
    }

    @Test
    void exposesTtlSeconds() {
        JwtService jwtService = new JwtService(SECRET, "erp-cashier", 10);
        assertEquals(600, jwtService.getAccessTokenTtlSeconds());
    }
}
