package dev.lu212.bv.ai;

import dev.lu212.bv.db.ProviderConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProviderManagerTest {

    private ProviderManager manager;
    private TestProvider testProvider;

    static class TestProvider implements AIProvider {
        private final String name;
        TestProvider(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public boolean isAvailable() { return true; }
        @Override public List<Model> getModels() {
            return List.of(new Model(name + "-model", "Test Model", 4096, true));
        }
        @Override public AIResponse chat(List<Message> messages, String modelId) {
            return new AIResponse("ok", 0, 0, modelId);
        }
        @Override public void chatStreaming(List<Message> messages, String modelId,
            java.util.function.Consumer<String> onChunk, Runnable onComplete,
            java.util.function.Consumer<Exception> onError) {
            onChunk.accept("ok");
            onComplete.run();
        }
        @Override public double costPerPromptToken() { return 0; }
        @Override public double costPerCompletionToken() { return 0; }
    }

    @BeforeEach
    void setUp() throws Exception {
        var conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS provider_configs (
                name TEXT PRIMARY KEY, api_key TEXT, base_url TEXT,
                is_active INTEGER DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        var repo = new ProviderConfigRepository(conn);
        manager = new ProviderManager(repo);
        testProvider = new TestProvider("test");
    }

    @Test
    void register_addsProvider() {
        manager.register(testProvider);
        assertTrue(manager.getProviderNames().contains("test"));
    }

    @Test
    void register_isCaseInsensitive() {
        manager.register(testProvider);
        assertEquals(testProvider, manager.getProvider("TEST"));
    }

    @Test
    void setActiveProvider_returnsTrueForValid() {
        manager.register(testProvider);
        assertTrue(manager.setActiveProvider("test"));
        assertEquals(testProvider, manager.activeProvider());
    }

    @Test
    void setActiveProvider_returnsFalseForInvalid() {
        assertFalse(manager.setActiveProvider("nonexistent"));
        assertNull(manager.activeProvider());
    }

    @Test
    void setActiveModel_validatesAgainstProviderModels() {
        manager.register(testProvider);
        manager.setActiveProvider("test");
        assertTrue(manager.setActiveModel("test-model"));
        assertEquals("test-model", manager.activeModelId());
    }

    @Test
    void setActiveModel_rejectsInvalidModel() {
        manager.register(testProvider);
        manager.setActiveProvider("test");
        assertFalse(manager.setActiveModel("invalid-model"));
        assertNull(manager.activeModelId());
    }

    @Test
    void getRegisteredProviders_returnsCopy() {
        manager.register(testProvider);
        var list = manager.getRegisteredProviders();
        assertEquals(1, list.size());
        assertEquals("test", list.get(0).getName());
    }
}
