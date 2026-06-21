package dev.lu212.bv.input;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class GlobalHotkeyManager implements NativeKeyListener, AutoCloseable {

    private final Map<Integer, HotkeyAction> keyActionMap = new ConcurrentHashMap<>();
    private final Map<HotkeyAction, Runnable> actionHandlers = new ConcurrentHashMap<>();
    private final Map<HotkeyAction, HotkeyBinding> bindings = new ConcurrentHashMap<>();

    private volatile boolean ctrlPressed;
    private volatile boolean altPressed;
    private volatile boolean shiftPressed;

    private volatile boolean registered;

    private final Consumer<String> onError;

    public GlobalHotkeyManager() {
        this(null);
    }

    public GlobalHotkeyManager(Consumer<String> onError) {
        this.onError = onError;
    }

    public void loadBindings(Map<HotkeyAction, HotkeyBinding> bindingsMap) {
        keyActionMap.clear();
        bindings.clear();
        for (var entry : bindingsMap.entrySet()) {
            var action = entry.getKey();
            var binding = entry.getValue();
            bindings.put(action, binding);
            keyActionMap.put(binding.keyCode(), action);
        }
    }

    public HotkeyBinding getBinding(HotkeyAction action) {
        return bindings.get(action);
    }

    public void bind(HotkeyAction action, int keyCode) {
        keyActionMap.put(keyCode, action);
    }

    public void onAction(HotkeyAction action, Runnable handler) {
        actionHandlers.put(action, handler);
    }

    public void rebind(HotkeyAction action, HotkeyBinding binding) {
        keyActionMap.entrySet().removeIf(e -> e.getValue() == action);
        keyActionMap.put(binding.keyCode(), action);
        bindings.put(action, binding);
    }

    public boolean register() {
        try {
            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook();
            }
            GlobalScreen.addNativeKeyListener(this);
            registered = true;
            return true;
        } catch (NativeHookException e) {
            var msg = "Failed to register global hotkeys: " + e.getMessage();
            System.err.println(msg);
            if (onError != null) onError.accept(msg);
            return false;
        }
    }

    public boolean isRegistered() {
        return registered;
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        switch (e.getKeyCode()) {
            case NativeKeyEvent.VC_CONTROL -> ctrlPressed = true;
            case NativeKeyEvent.VC_ALT -> altPressed = true;
            case NativeKeyEvent.VC_SHIFT -> shiftPressed = true;
        }

        if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL
            || e.getKeyCode() == NativeKeyEvent.VC_ALT
            || e.getKeyCode() == NativeKeyEvent.VC_SHIFT) {
            return;
        }

        var action = keyActionMap.get(e.getKeyCode());
        if (action == null) return;

        var binding = bindings.get(action);
        if (binding == null) return;

        if (binding.ctrlRequired() == ctrlPressed
            && binding.altRequired() == altPressed
            && binding.shiftRequired() == shiftPressed) {
            var handler = actionHandlers.get(action);
            if (handler != null) {
                new Thread(handler, "Hotkey-" + action.name()).start();
            }
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        switch (e.getKeyCode()) {
            case NativeKeyEvent.VC_CONTROL -> ctrlPressed = false;
            case NativeKeyEvent.VC_ALT -> altPressed = false;
            case NativeKeyEvent.VC_SHIFT -> shiftPressed = false;
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {}

    @Override
    public void close() {
        try {
            GlobalScreen.removeNativeKeyListener(this);
        } catch (Exception ignored) {}
    }
}
