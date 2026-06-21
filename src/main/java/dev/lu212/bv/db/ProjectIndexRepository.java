package dev.lu212.bv.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ProjectIndexRepository {

    private final Connection conn;

    public ProjectIndexRepository(Connection conn) {
        this.conn = conn;
    }

    public boolean isIndexed(String projectPath) {
        try (var ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM project_index_meta WHERE project_path = ?")) {
            ps.setString(1, projectPath);
            var rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public void markIndexed(String projectPath, int fileCount) {
        try (var ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO project_index_meta (project_path, file_count, is_indexed) VALUES (?, ?, 1)")) {
            ps.setString(1, projectPath);
            ps.setInt(2, fileCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error marking project indexed: " + e.getMessage());
        }
    }

    public void saveClassInfo(String projectPath, String className, String filePath, String description, String methodsJson) {
        try (var ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO project_index (project_path, class_name, file_path, description, methods_json) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, projectPath);
            ps.setString(2, className);
            ps.setString(3, filePath);
            ps.setString(4, description);
            ps.setString(5, methodsJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving class info: " + e.getMessage());
        }
    }

    public List<IndexedClass> getClasses(String projectPath) {
        var result = new ArrayList<IndexedClass>();
        try (var ps = conn.prepareStatement(
                "SELECT class_name, file_path, description, methods_json FROM project_index WHERE project_path = ? ORDER BY class_name")) {
            ps.setString(1, projectPath);
            var rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new IndexedClass(
                    rs.getString("class_name"),
                    rs.getString("file_path"),
                    rs.getString("description"),
                    rs.getString("methods_json")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching indexed classes: " + e.getMessage());
        }
        return result;
    }

    public void clear(String projectPath) {
        try (var ps = conn.prepareStatement("DELETE FROM project_index WHERE project_path = ?")) {
            ps.setString(1, projectPath);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error clearing project index: " + e.getMessage());
        }
        try (var ps = conn.prepareStatement("DELETE FROM project_index_meta WHERE project_path = ?")) {
            ps.setString(1, projectPath);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error clearing project index meta: " + e.getMessage());
        }
    }

    public List<IndexedClass> getClassesByFile(String projectPath, String filePath) {
        var result = new ArrayList<IndexedClass>();
        try (var ps = conn.prepareStatement(
                "SELECT class_name, file_path, description, methods_json FROM project_index WHERE project_path = ? AND file_path = ?")) {
            ps.setString(1, projectPath);
            ps.setString(2, filePath);
            var rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new IndexedClass(
                    rs.getString("class_name"),
                    rs.getString("file_path"),
                    rs.getString("description"),
                    rs.getString("methods_json")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching classes by file: " + e.getMessage());
        }
        return result;
    }

    public String buildClassContextForFiles(String projectPath, List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) return "";
        var sb = new StringBuilder();
        sb.append("## Betroffene Klassen (aus Index)\n");
        for (var fp : filePaths) {
            var classes = getClassesByFile(projectPath, fp);
            if (classes.isEmpty()) continue;
            for (var cls : classes) {
                sb.append("- **").append(cls.className()).append("** in `").append(cls.filePath()).append("`");
                if (cls.description() != null && !cls.description().isBlank()) {
                    sb.append(": ").append(cls.description());
                }
                if (cls.methodsJson() != null && !cls.methodsJson().isBlank()) {
                    sb.append("\n  Methoden: ").append(cls.methodsJson());
                }
                sb.append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    public String buildIndexSummary(String projectPath) {
        var classes = getClasses(projectPath);
        if (classes.isEmpty()) return "";

        var sb = new StringBuilder();
        sb.append("## Indexierte Projekt-Struktur\n");
        sb.append("Das Projekt wurde bereits analysiert. Folgende Klassen sind bekannt:\n\n");
        for (var cls : classes) {
            sb.append("### ").append(cls.className()).append("\n");
            sb.append("- **Datei:** `").append(cls.filePath()).append("`\n");
            if (cls.description() != null && !cls.description().isBlank()) {
                sb.append("- **Beschreibung:** ").append(cls.description()).append("\n");
            }
            if (cls.methodsJson() != null && !cls.methodsJson().isBlank()) {
                sb.append("- **Methoden:** ").append(cls.methodsJson()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public record IndexedClass(String className, String filePath, String description, String methodsJson) {}
}
