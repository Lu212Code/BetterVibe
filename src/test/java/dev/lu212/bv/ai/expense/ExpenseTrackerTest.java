package dev.lu212.bv.ai.expense;

import dev.lu212.bv.ai.AIProvider;
import dev.lu212.bv.ai.AIResponse;
import dev.lu212.bv.ai.Model;
import dev.lu212.bv.db.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseTrackerTest {

    private ExpenseTracker tracker;
    private ExpenseRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        var conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                provider TEXT, model TEXT, prompt_tokens INTEGER,
                completion_tokens INTEGER, cost REAL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        repo = new ExpenseRepository(conn);
        tracker = new ExpenseTracker(repo);
    }

    @Test
    void track_recordsExpense() {
        var provider = new TestProvider();
        var response = new AIResponse("test", 100, 50, "test-model");
        tracker.track(provider, response);

        assertEquals(0.0005, tracker.getTotalCost(), 0.00001); // 100*0 + 50*0.00001
    }

    @Test
    void getTotalCostFormatted_returnsFormattedString() {
        var provider = new TestProvider();
        tracker.track(provider, new AIResponse("test", 100, 50, "test-model"));
        assertTrue(tracker.getTotalCostFormatted().startsWith("$"));
    }

    @Test
    void getSummary_returnsCorrectStats() {
        var provider = new TestProvider();
        tracker.track(provider, new AIResponse("a", 200, 30, "m"));
        tracker.track(provider, new AIResponse("b", 100, 20, "m"));

        var summary = tracker.getSummary();
        assertEquals(2, summary.totalCalls());
        assertEquals(300, summary.promptTokens());
        assertEquals(50, summary.completionTokens());
        assertTrue(summary.totalCost() > 0);
    }

    @Test
    void emptyTracker_returnsZeroSummary() {
        assertEquals(0.0, tracker.getTotalCost(), 0.00001);
        var s = tracker.getSummary();
        assertEquals(0, s.totalCalls());
        assertEquals(0, s.promptTokens());
    }

    static class TestProvider implements AIProvider {
        @Override public String getName() { return "test"; }
        @Override public boolean isAvailable() { return true; }
        @Override public List<Model> getModels() {
            return List.of(new Model("test-model", "Test", 4096, true));
        }
        @Override public AIResponse chat(List<dev.lu212.bv.ai.Message> messages, String modelId) {
            return new AIResponse("ok", 0, 0, modelId);
        }
        @Override public void chatStreaming(List<dev.lu212.bv.ai.Message> messages, String modelId,
            java.util.function.Consumer<String> onChunk, Runnable onComplete,
            java.util.function.Consumer<Exception> onError) {
            onChunk.accept("ok"); onComplete.run();
        }
        @Override public double costPerPromptToken() { return 0; }
        @Override public double costPerCompletionToken() { return 0.00001; }
    }
}
