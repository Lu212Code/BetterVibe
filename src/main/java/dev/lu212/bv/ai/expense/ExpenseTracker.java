package dev.lu212.bv.ai.expense;

import dev.lu212.bv.ai.AIResponse;
import dev.lu212.bv.ai.AIProvider;
import dev.lu212.bv.db.ExpenseRepository;

import java.time.LocalDate;

public final class ExpenseTracker {

    private final ExpenseRepository repo;

    public ExpenseTracker(ExpenseRepository repo) {
        this.repo = repo;
    }

    public void track(AIProvider provider, AIResponse response) {
        var promptCost = response.promptTokens() * provider.costPerPromptToken();
        var completionCost = response.completionTokens() * provider.costPerCompletionToken();
        var totalCost = promptCost + completionCost;

        repo.addExpense(
            provider.getName(),
            response.model(),
            response.promptTokens(),
            response.completionTokens(),
            totalCost
        );
    }

    public String getTotalCostFormatted() {
        return String.format("$%.4f", repo.getTotalCost());
    }

    public String getCostTodayFormatted() {
        return String.format("$%.4f", repo.getCostSince(LocalDate.now()));
    }

    public double getTotalCost() {
        return repo.getTotalCost();
    }

    public ExpenseRepository.ExpenseSummary getSummary() {
        return repo.getSummary().orElse(new ExpenseRepository.ExpenseSummary(0, 0, 0.0, 0));
    }
}
