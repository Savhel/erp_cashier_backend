package com.erp.cashier.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordServiceTest {
    private final PasswordService passwordService = new PasswordService();

    @Test
    void verifiesPlaintextPasswords() {
        assertTrue(passwordService.verifyPassword("secret", "secret"));
        assertFalse(passwordService.verifyPassword("secret", "other"));
    }

    @Test
    void hashesAndVerifiesScryptPasswords() {
        String hash = passwordService.hashPassword("secret");

        assertTrue(hash.contains(":"));
        assertTrue(passwordService.verifyPassword("secret", hash));
        assertFalse(passwordService.verifyPassword("wrong", hash));
    }

    @Test
    void hashPasswordRejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> passwordService.hashPassword(null));
    }
}
