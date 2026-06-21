package dev.lu212.bv.input;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

public enum HotkeyAction {
    REVIEW_CODE(NativeKeyEvent.VC_F9, true, false, false, "Review"),
    TOGGLE_OVERLAY(NativeKeyEvent.VC_F11, true, false, true, "Overlay");

    private final int defaultKeyCode;
    private final boolean ctrlRequired;
    private final boolean altRequired;
    private final boolean shiftRequired;
    private final String description;

    HotkeyAction(int defaultKeyCode, boolean ctrlRequired, boolean altRequired, boolean shiftRequired, String description) {
        this.defaultKeyCode = defaultKeyCode;
        this.ctrlRequired = ctrlRequired;
        this.altRequired = altRequired;
        this.shiftRequired = shiftRequired;
        this.description = description;
    }

    public int defaultKeyCode() { return defaultKeyCode; }
    public String description() { return description; }

    public String defaultKeyName() {
        return NativeKeyEvent.getKeyText(defaultKeyCode);
    }

    public HotkeyBinding defaultBinding() {
        return new HotkeyBinding(defaultKeyCode, defaultKeyName(), ctrlRequired, altRequired, shiftRequired);
    }

    public static int keyCodeForName(String name) {
        for (var f : NativeKeyEvent.class.getFields()) {
            if (f.getName().equals("VC_" + name.toUpperCase())) {
                try { return f.getInt(null); } catch (Exception ignored) {}
            }
        }
        return -1;
    }
}
