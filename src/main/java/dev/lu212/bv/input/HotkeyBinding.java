package dev.lu212.bv.input;

public record HotkeyBinding(int keyCode, String keyName, boolean ctrlRequired, boolean altRequired, boolean shiftRequired) {

    public HotkeyBinding(int keyCode, String keyName) {
        this(keyCode, keyName, true, false, false);
    }

    public String displayString(HotkeyAction action) {
        var sb = new StringBuilder();
        if (ctrlRequired) sb.append("Ctrl+");
        if (altRequired) sb.append("Alt+");
        if (shiftRequired) sb.append("Shift+");
        sb.append(keyName);
        return sb.toString();
    }
}
