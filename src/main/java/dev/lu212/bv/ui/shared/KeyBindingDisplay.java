package dev.lu212.bv.ui.shared;

import dev.lu212.bv.input.HotkeyAction;
import dev.lu212.bv.input.HotkeyBinding;

import java.util.Map;

public final class KeyBindingDisplay {

    private KeyBindingDisplay() {}

    public static String format(HotkeyAction action, HotkeyBinding binding) {
        return binding.displayString(action) + " " + action.description();
    }

    public static String allBindings(Map<HotkeyAction, HotkeyBinding> bindings) {
        var sb = new StringBuilder();
        for (var entry : bindings.entrySet()) {
            if (!sb.isEmpty()) sb.append(" │ ");
            sb.append(format(entry.getKey(), entry.getValue()));
        }
        return sb.toString();
    }
}
