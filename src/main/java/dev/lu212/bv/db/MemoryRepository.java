package dev.lu212.bv.db;

import dev.lu212.bv.ai.memory.Memory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MemoryRepository {

    private final Connection conn;

    public MemoryRepository(Connection conn) {
        this.conn = conn;
    }

    public void save(String projectPath, String summary, int tokenCount) {
        try (var ps = conn.prepareStatement("""
                INSERT INTO memories (project_path, summary, token_count, created_at)
                VALUES (?, ?, ?, ?)
            """)) {
            ps.setString(1, projectPath);
            ps.setString(2, summary);
            ps.setInt(3, tokenCount);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving memory: " + e.getMessage());
        }
    }

    public List<Memory> getMemories(String projectPath, int limit) {
        var result = new ArrayList<Memory>();
        try (var ps = conn.prepareStatement("""
                SELECT summary, token_count, created_at FROM memories
                WHERE project_path = ?
                ORDER BY created_at DESC LIMIT ?
            """)) {
            ps.setString(1, projectPath);
            ps.setInt(2, limit);
            var rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new Memory(
                    rs.getString("summary"),
                    rs.getInt("token_count"),
                    rs.getTimestamp("created_at").toInstant()
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching memories: " + e.getMessage());
        }
        return result;
    }

    public int countMemories(String projectPath) {
        try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM memories WHERE project_path = ?")) {
            ps.setString(1, projectPath);
            var rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Error counting memories: " + e.getMessage());
        }
        return 0;
    }

    public void deleteOldest(String projectPath, int keepCount) {
        try (var ps = conn.prepareStatement("""
                DELETE FROM memories WHERE id IN (
                    SELECT id FROM memories WHERE project_path = ?
                    ORDER BY created_at ASC
                    LIMIT MAX(0, (SELECT COUNT(*) FROM memories WHERE project_path = ?) - ?)
                )
            """)) {
            ps.setString(1, projectPath);
            ps.setString(2, projectPath);
            ps.setInt(3, keepCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting old memories: " + e.getMessage());
        }
    }

    public void clear(String projectPath) {
        try (var ps = conn.prepareStatement("DELETE FROM memories WHERE project_path = ?")) {
            ps.setString(1, projectPath);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error clearing memories: " + e.getMessage());
        }
    }
}
