package com.lhkeeper.ticketing.railway_ticketing.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // 32-byte secret for HS256
        jwtUtil = new JwtUtil("test-secret-key-12345678901234567890", 3600);
    }

    @Test
    void generateToken_shouldIncludeRole() {
        String token = jwtUtil.generateToken(1L, "admin", "13800000000", 1);
        Claims claims = jwtUtil.parseToken(token);

        assertEquals("1", claims.getSubject());
        assertEquals("admin", claims.get("username", String.class));
        assertEquals("13800000000", claims.get("phone", String.class));
        assertEquals(1, claims.get("role", Integer.class));
    }

    @Test
    void generateToken_userRoleShouldBeZero() {
        String token = jwtUtil.generateToken(2L, "user", "13900000000", 0);
        Claims claims = jwtUtil.parseToken(token);

        assertEquals(0, claims.get("role", Integer.class));
    }

    @Test
    void tokenExpiry_shouldWork() throws InterruptedException {
        JwtUtil shortLived = new JwtUtil("test-secret-key-12345678901234567890", 1);
        String token = shortLived.generateToken(1L, "test", "13800000000", 0);
        assertFalse(shortLived.isTokenExpired(token));
        Thread.sleep(1100);
        assertTrue(shortLived.isTokenExpired(token));
    }

    @Test
    void parseToken_invalidToken_shouldThrow() {
        assertThrows(Exception.class, () -> jwtUtil.parseToken("invalid-token"));
    }
}
