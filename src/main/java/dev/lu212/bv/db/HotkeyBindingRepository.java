package dev.lu212.bv.db;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import dev.lu212.bv.input.HotkeyAction;
import dev.lu212.bv.input.HotkeyBinding;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class HotkeyBindingRepository {

    private final Connection conn;

    public HotkeyBindingRepository(Connection conn) {
        this.conn = conn;
    }

    public Map<HotkeyAction, HotkeyBinding> loadAll() {
        var result = new LinkedHashMap<HotkeyAction, HotkeyBinding>();
        for (var action : HotkeyAction.values()) {
            result.put(action, load(action).orElseGet(action::defaultBinding));
        }
        return result;
    }

    public Optional<HotkeyBinding> load(HotkeyAction action) {
        try (var ps = conn.prepareStatement(
            "SELECT key_code, key_name, ctrl_required, alt_required, shift_required FROM key_bindings WHERE action_name = ?"
        )) {
            ps.setString(1, action.name());
            var rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(new HotkeyBinding(
                    rs.getInt("key_code"),
                    rs.getString("key_name"),
                    rs.getInt("ctrl_required") == 1,
                    rs.getInt("alt_required") == 1,
                    rs.getInt("shift_required") == 1
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error loading key binding: " + e.getMessage());
        }
        return Optional.empty();
    }

    public void save(HotkeyAction action, HotkeyBinding binding) {
        try (var ps = conn.prepareStatement("""
            INSERT INTO key_bindings (action_name, key_code, key_name, ctrl_required, alt_required, shift_required)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(action_name) DO UPDATE SET
                key_code = excluded.key_code,
                key_name = excluded.key_name,
                ctrl_required = excluded.ctrl_required,
                alt_required = excluded.alt_required,
                shift_required = excluded.shift_required
        """)) {
            ps.setString(1, action.name());
            ps.setInt(2, binding.keyCode());
            ps.setString(3, binding.keyName());
            ps.setInt(4, binding.ctrlRequired() ? 1 : 0);
            ps.setInt(5, binding.altRequired() ? 1 : 0);
            ps.setInt(6, binding.shiftRequired() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving key binding: " + e.getMessage());
        }
    }

    public void delete(HotkeyAction action) {
        try (var ps = conn.prepareStatement("DELETE FROM key_bindings WHERE action_name = ?")) {
            ps.setString(1, action.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting key binding: " + e.getMessage());
        }
    }
}
