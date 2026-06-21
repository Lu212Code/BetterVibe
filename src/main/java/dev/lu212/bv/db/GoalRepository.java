package dev.lu212.bv.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GoalRepository {

    public record ProjectInfo(String projectPath, String goal, String mode, String providerName, String modelName) {}

    private final Connection conn;

    public GoalRepository(Connection conn) {
        this.conn = conn;
    }

    public List<ProjectInfo> getAllProjects() {
        var result = new ArrayList<ProjectInfo>();
        try (var ps = conn.prepareStatement(
                "SELECT project_path, current_goal, mode, provider_name, model_name FROM project_config ORDER BY updated_at DESC")) {
            var rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new ProjectInfo(
                    rs.getString("project_path"),
                    rs.getString("current_goal"),
                    rs.getString("mode"),
                    rs.getString("provider_name"),
                    rs.getString("model_name")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all projects: " + e.getMessage());
        }
        return result;
    }

    public Optional<String> getGoal(String projectPath) {
        try (var ps = conn.prepareStatement("SELECT current_goal FROM project_config WHERE project_path = ?")) {
            ps.setString(1, projectPath);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.ofNullable(rs.getString("current_goal"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching goal: " + e.getMessage());
        }
        return Optional.empty();
    }

    public void setGoal(String projectPath, String goal) {
        try (var ps = conn.prepareStatement("""
                INSERT INTO project_config (project_path, current_goal, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(project_path) DO UPDATE SET current_goal = excluded.current_goal, updated_at = excluded.updated_at
            """)) {
            ps.setString(1, projectPath);
            ps.setString(2, goal);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving goal: " + e.getMessage());
        }
    }

    public Optional<String> getProvider(String projectPath) {
        try (var ps = conn.prepareStatement("SELECT provider_name, model_name FROM project_config WHERE project_path = ?")) {
            ps.setString(1, projectPath);
            var rs = ps.executeQuery();
            if (rs.next()) {
                var p = rs.getString("provider_name");
                var m = rs.getString("model_name");
                if (p != null && m != null) return Optional.of(p + ":" + m);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching provider: " + e.getMessage());
        }
        return Optional.empty();
    }

    public void setProvider(String projectPath, String providerName, String modelName) {
        try (var ps = conn.prepareStatement("""
                INSERT INTO project_config (project_path, provider_name, model_name, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(project_path) DO UPDATE SET provider_name = excluded.provider_name, model_name = excluded.model_name, updated_at = excluded.updated_at
            """)) {
            ps.setString(1, projectPath);
            ps.setString(2, providerName);
            ps.setString(3, modelName);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving provider: " + e.getMessage());
        }
    }

    public Optional<String> getMode(String projectPath) {
        try (var ps = conn.prepareStatement("SELECT mode FROM project_config WHERE project_path = ?")) {
            ps.setString(1, projectPath);
            var rs = ps.executeQuery();
            if (rs.next()) return Optional.ofNullable(rs.getString("mode"));
        } catch (SQLException e) {
            System.err.println("Error fetching mode: " + e.getMessage());
        }
        return Optional.empty();
    }

    public void delete(String projectPath) {
        try (var ps = conn.prepareStatement("DELETE FROM project_config WHERE project_path = ?")) {
            ps.setString(1, projectPath);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting project: " + e.getMessage());
        }
    }

    public void setMode(String projectPath, String mode) {
        try (var ps = conn.prepareStatement("""
                INSERT INTO project_config (project_path, mode, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(project_path) DO UPDATE SET mode = excluded.mode, updated_at = excluded.updated_at
            """)) {
            ps.setString(1, projectPath);
            ps.setString(2, mode);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving mode: " + e.getMessage());
        }
    }
}
