package dev.lu212.bv.ai;

import java.util.List;

public record Model(String id, String displayName, int contextWindow, boolean supportsStreaming) {}
