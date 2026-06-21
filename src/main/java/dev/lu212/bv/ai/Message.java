package dev.lu212.bv.ai;

public record Message(String role, String content, int tokens) {
    public Message {
        if (role == null || content == null) {
            throw new IllegalArgumentException("role and content must not be null");
        }
    }

    public Message(String role, String content) {
        this(role, content, 0);
    }

    public long estimatedTokens() {
        if (tokens > 0) return tokens;
        return (long) (content.length() / 4.0);
    }
}
