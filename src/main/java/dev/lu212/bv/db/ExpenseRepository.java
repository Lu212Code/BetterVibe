package dev.lu212.bv.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public final class ExpenseRepository {

    private final Connection conn;

    public ExpenseRepository(Connection conn) {
        this.conn = conn;
    }

    public void addExpense(String provider, String model, int promptTokens, int completionTokens, double cost) {
        try (var ps = conn.prepareStatement("""
                INSERT INTO expenses (provider, model, prompt_tokens, completion_tokens, cost, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, provider);
            ps.setString(2, model);
            ps.setInt(3, promptTokens);
            ps.setInt(4, completionTokens);
            ps.setDouble(5, cost);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding expense: " + e.getMessage());
        }
    }

    public double getTotalCost() {
        try (var ps = conn.prepareStatement("SELECT COALESCE(SUM(cost), 0) FROM expenses")) {
            var rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Error fetching total cost: " + e.getMessage());
        }
        return 0.0;
    }

    public double getCostSince(LocalDate date) {
        try (var ps = conn.prepareStatement("SELECT COALESCE(SUM(cost), 0) FROM expenses WHERE created_at >= ?")) {
            ps.setTimestamp(1, Timestamp.valueOf(date.atStartOfDay()));
            var rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Error fetching cost since: " + e.getMessage());
        }
        return 0.0;
    }

    public Optional<ExpenseSummary> getSummary() {
        try (var ps = conn.prepareStatement("""
                SELECT COALESCE(SUM(prompt_tokens), 0) AS pt,
                       COALESCE(SUM(completion_tokens), 0) AS ct,
                       COALESCE(SUM(cost), 0) AS cost,
                       COUNT(*) AS calls
                FROM expenses
            """)) {
            var rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(new ExpenseSummary(
                    rs.getInt("pt"),
                    rs.getInt("ct"),
                    rs.getDouble("cost"),
                    rs.getInt("calls")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching expense summary: " + e.getMessage());
        }
        return Optional.empty();
    }

    public record ExpenseSummary(int promptTokens, int completionTokens, double totalCost, int totalCalls) {}
}
