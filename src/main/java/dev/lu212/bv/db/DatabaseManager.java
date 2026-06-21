package dev.lu212.bv.db;

import dev.lu212.bv.AppDefaults;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseManager implements AutoCloseable {

    private final Connection connection;

    public DatabaseManager() {
        try {
            Files.createDirectories(Path.of(AppDefaults.DB_DIR));
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + AppDefaults.DB_PATH);
            try (var stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            initTables();
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private void initTables() throws SQLException {
        try (var stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS project_config (
                    project_path TEXT PRIMARY KEY,
                    current_goal TEXT,
                    provider_name TEXT,
                    model_name TEXT,
                    mode TEXT DEFAULT 'manual',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS memories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    project_path TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    token_count INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    project_path TEXT NOT NULL,
                    role TEXT NOT NULL CHECK(role IN ('system','user','assistant')),
                    content TEXT NOT NULL,
                    tokens INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS provider_configs (
                    name TEXT PRIMARY KEY,
                    api_key TEXT,
                    base_url TEXT,
                    is_active INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS key_bindings (
                    action_name TEXT PRIMARY KEY,
                    key_code INTEGER NOT NULL,
                    key_name TEXT NOT NULL,
                    ctrl_required INTEGER DEFAULT 1,
                    alt_required INTEGER DEFAULT 1,
                    shift_required INTEGER DEFAULT 1
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS project_index_meta (
                    project_path TEXT PRIMARY KEY,
                    file_count INTEGER DEFAULT 0,
                    is_indexed INTEGER DEFAULT 0,
                    indexed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS project_index (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    project_path TEXT NOT NULL,
                    class_name TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    description TEXT,
                    methods_json TEXT,
                    UNIQUE(project_path, class_name)
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    provider TEXT NOT NULL,
                    model TEXT NOT NULL,
                    prompt_tokens INTEGER DEFAULT 0,
                    completion_tokens INTEGER DEFAULT 0,
                    cost REAL DEFAULT 0.0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing database: " + e.getMessage());
        }
    }
}
