package com.github.sunrishe.legado_idea.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlValidator {
    private static final Pattern PATTERN = Pattern.compile(
            "^https?://((?:\\d{1,3}\\.){3}(?:\\d{1,3})):(\\d{1,5})$"
    );

    private UrlValidator() {}

    public static boolean isValid(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        Matcher matcher = PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            return false;
        }
        String[] parts = matcher.group(1).split("\\.");
        for (String part : parts) {
            int value = Integer.parseInt(part);
            if (value > 255) {
                return false;
            }
        }
        int port = Integer.parseInt(matcher.group(2));
        return port <= 65535;
    }
}
