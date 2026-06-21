package dev.lu212.bv.ui;

public enum AppMode {
    OVERLAY,
    CLI;

    public static AppMode fromString(String s) {
        if (s == null) return OVERLAY;
        return switch (s.toLowerCase()) {
            case "cli" -> CLI;
            default -> OVERLAY;
        };
    }
}
