package dev.lu212.bv.util;

import java.util.Locale;

public final class TokenCounter {

    private TokenCounter() {}

    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }

    public static int estimateTokensForDiff(String diff) {
        return estimateTokens(diff);
    }

    public static String formatTokenCount(int tokens) {
        if (tokens < 1000) return tokens + " tokens";
        return String.format(Locale.US, "%.1fk tokens", tokens / 1000.0);
    }
}
