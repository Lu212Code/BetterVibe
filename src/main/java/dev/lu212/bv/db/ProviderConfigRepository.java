package dev.lu212.bv.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProviderConfigRepository {

    public record ProviderEntry(String name, String apiKey, String baseUrl, boolean active) {}

    private final Connection conn;

    public ProviderConfigRepository(Connection conn) {
        this.conn = conn;
    }

    public List<ProviderEntry> getAll() {
        var result = new ArrayList<ProviderEntry>();
        try (var ps = conn.prepareStatement("SELECT name, api_key, base_url, is_active FROM provider_configs ORDER BY created_at")) {
            var rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new ProviderEntry(
                    rs.getString("name"),
                    rs.getString("api_key"),
                    rs.getString("base_url"),
                    rs.getInt("is_active") == 1
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching provider configs: " + e.getMessage());
        }
        return result;
    }

    public void save(String name, String apiKey, String baseUrl) {
        try (var ps = conn.prepareStatement("""
                INSERT INTO provider_configs (name, api_key, base_url, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(name) DO UPDATE SET api_key = excluded.api_key, base_url = excluded.base_url
            """)) {
            ps.setString(1, name);
            ps.setString(2, apiKey);
            ps.setString(3, baseUrl);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving provider config: " + e.getMessage());
        }
    }

    public Optional<String> getApiKey(String name) {
        try (var ps = conn.prepareStatement("SELECT api_key FROM provider_configs WHERE name = ?")) {
            ps.setString(1, name);
            var rs = ps.executeQuery();
            if (rs.next()) return Optional.ofNullable(rs.getString("api_key"));
        } catch (SQLException e) {
            System.err.println("Error fetching API key: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<String> getBaseUrl(String name) {
        try (var ps = conn.prepareStatement("SELECT base_url FROM provider_configs WHERE name = ?")) {
            ps.setString(1, name);
            var rs = ps.executeQuery();
            if (rs.next()) return Optional.ofNullable(rs.getString("base_url"));
        } catch (SQLException e) {
            System.err.println("Error fetching base URL: " + e.getMessage());
        }
        return Optional.empty();
    }

    public void setActive(String name) {
        try (var stmt = conn.createStatement();
             var ps = conn.prepareStatement("UPDATE provider_configs SET is_active = 1 WHERE name = ?")) {
            stmt.execute("UPDATE provider_configs SET is_active = 0");
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error setting active provider: " + e.getMessage());
        }
    }

    public Optional<String> getActiveProvider() {
        try (var ps = conn.prepareStatement("SELECT name FROM provider_configs WHERE is_active = 1")) {
            var rs = ps.executeQuery();
            if (rs.next()) return Optional.ofNullable(rs.getString("name"));
        } catch (SQLException e) {
            System.err.println("Error fetching active provider: " + e.getMessage());
        }
        return Optional.empty();
    }

    public void delete(String name) {
        try (var ps = conn.prepareStatement("DELETE FROM provider_configs WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting provider config: " + e.getMessage());
        }
    }
}
