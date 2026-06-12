package com.github.sunrishe.legado_idea.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    @Test
    void acceptsValidHttpUrl() {
        assertTrue(UrlValidator.isValid("http://192.168.1.2:1122"));
    }

    @Test
    void acceptsValidHttpsUrl() {
        assertTrue(UrlValidator.isValid("https://10.0.0.1:8080"));
    }

    @Test
    void rejectsInvalidIp() {
        assertFalse(UrlValidator.isValid("http://192.168.1.256:1122"));
    }

    @Test
    void rejectsInvalidPort() {
        assertFalse(UrlValidator.isValid("http://192.168.1.2:70000"));
    }

    @Test
    void rejectsMissingScheme() {
        assertFalse(UrlValidator.isValid("192.168.1.2:1122"));
    }
}
