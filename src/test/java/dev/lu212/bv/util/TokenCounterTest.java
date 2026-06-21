package dev.lu212.bv.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenCounterTest {

    @Test
    void estimateTokens_returnsZeroForNull() {
        assertEquals(0, TokenCounter.estimateTokens(null));
    }

    @Test
    void estimateTokens_returnsZeroForBlank() {
        assertEquals(0, TokenCounter.estimateTokens("   "));
    }

    @Test
    void estimateTokens_roughlyOneFourthOfLength() {
        var text = "abcdefghijklmnopqrstuvwxyz0123456789!";
        assertEquals(10, TokenCounter.estimateTokens(text));
    }

    @Test
    void estimateTokens_roundsUp() {
        var text = "abc"; // 3 chars / 4 = 0.75 -> 1
        assertEquals(1, TokenCounter.estimateTokens(text));
    }

    @Test
    void estimateTokensForDiff_delegatesToEstimateTokens() {
        var diff = "diff --git a/file b/file\n+new line\n-old line\n";
        assertEquals(TokenCounter.estimateTokens(diff), TokenCounter.estimateTokensForDiff(diff));
    }

    @Test
    void formatTokenCount_underThousand() {
        assertEquals("500 tokens", TokenCounter.formatTokenCount(500));
    }

    @Test
    void formatTokenCount_thousands() {
        assertEquals("1.5k tokens", TokenCounter.formatTokenCount(1500));
    }

    @Test
    void formatTokenCount_exactThousand() {
        assertEquals("1.0k tokens", TokenCounter.formatTokenCount(1000));
    }
}
