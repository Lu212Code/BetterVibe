package dev.lu212.bv.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface AIProvider {

    String getName();

    boolean isAvailable();

    List<Model> getModels();

    AIResponse chat(List<Message> messages, String modelId);

    void chatStreaming(
        List<Message> messages,
        String modelId,
        Consumer<String> onChunk,
        Runnable onComplete,
        Consumer<Exception> onError
    );

    default CompletableFuture<AIResponse> chatAsync(List<Message> messages, String modelId) {
        return CompletableFuture.supplyAsync(() -> chat(messages, modelId));
    }

    double costPerPromptToken();

    double costPerCompletionToken();
}
