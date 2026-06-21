package dev.lu212.bv.db;

import dev.lu212.bv.ai.Message;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MessageRepository {

    private final Connection conn;

    public MessageRepository(Connection conn) {
        this.conn = conn;
    }

    public void save(String projectPath, String role, String content, int tokens) {
        try (var ps = conn.prepareStatement("""
                INSERT INTO messages (project_path, role, content, tokens, created_at)
                VALUES (?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, projectPath);
            ps.setString(2, role);
            ps.setString(3, content);
            ps.setInt(4, tokens);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving message: " + e.getMessage());
        }
    }

    public List<Message> getRecent(String projectPath, int limit) {
        return getRecent(projectPath, limit, 0);
    }

    public List<Message> getRecent(String projectPath, int limit, int offset) {
        var result = new ArrayList<Message>();
        try (var ps = conn.prepareStatement("""
                SELECT role, content, tokens, created_at FROM messages
                WHERE project_path = ?
                ORDER BY created_at DESC LIMIT ? OFFSET ?
            """)) {
            ps.setString(1, projectPath);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            var rs = ps.executeQuery();
            while (rs.next()) {
                result.add(0, new Message(
                    rs.getString("role"),
                    rs.getString("content"),
                    rs.getInt("tokens")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching messages: " + e.getMessage());
        }
        return result;
    }

    public int countMessages(String projectPath) {
        try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM messages WHERE project_path = ?")) {
            ps.setString(1, projectPath);
            var rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Error counting messages: " + e.getMessage());
        }
        return 0;
    }

    public void deleteOlderThan(String projectPath, int keepCount) {
        try (var ps = conn.prepareStatement("""
                DELETE FROM messages WHERE id IN (
                    SELECT id FROM messages WHERE project_path = ?
                    ORDER BY created_at ASC
                    LIMIT MAX(0, (SELECT COUNT(*) FROM messages WHERE project_path = ?) - ?)
                )
            """)) {
            ps.setString(1, projectPath);
            ps.setString(2, projectPath);
            ps.setInt(3, keepCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting old messages: " + e.getMessage());
        }
    }

    public void clear(String projectPath) {
        try (var ps = conn.prepareStatement("DELETE FROM messages WHERE project_path = ?")) {
            ps.setString(1, projectPath);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error clearing messages: " + e.getMessage());
        }
    }
}
