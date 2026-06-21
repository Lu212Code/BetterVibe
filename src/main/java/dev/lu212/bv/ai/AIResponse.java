package dev.lu212.bv.ai;

public record AIResponse(String content, int promptTokens, int completionTokens, String model) {
    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
