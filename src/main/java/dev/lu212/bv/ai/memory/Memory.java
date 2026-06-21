package dev.lu212.bv.ai.memory;

import java.time.Instant;

public record Memory(String summary, int tokenCount, Instant createdAt) {}
