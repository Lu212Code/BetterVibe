package dev.lu212.bv.ai.providers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.lu212.bv.ai.AIProvider;
import dev.lu212.bv.ai.AIResponse;
import dev.lu212.bv.ai.Message;
import dev.lu212.bv.ai.Model;

import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class OllamaProvider implements AIProvider {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final List<Model> MODELS = List.of(
        new Model("llama3.2", "Llama 3.2", 131072, true),
        new Model("llama3.1", "Llama 3.1", 131072, true),
        new Model("mixtral", "Mixtral 8x7B", 32768, true),
        new Model("codellama", "CodeLlama", 16384, true),
        new Model("deepseek-coder", "DeepSeek Coder", 16384, true),
        new Model("qwen2.5-coder", "Qwen 2.5 Coder", 32768, true),
        new Model("mistral", "Mistral", 32768, true)
    );

    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private final String baseUrl;

    public OllamaProvider() {
        this(DEFAULT_BASE_URL);
    }

    public OllamaProvider(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String getName() {
        return "ollama";
    }

    @Override
    public boolean isAvailable() {
        try {
            var request = new Request.Builder()
                .url(baseUrl + "/api/tags")
                .get()
                .build();
            try (var response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<Model> getModels() {
        try {
            var request = new Request.Builder()
                .url(baseUrl + "/api/tags")
                .get()
                .build();
            try (var response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    var obj = gson.fromJson(response.body().string(), JsonObject.class);
                    var models = obj.getAsJsonArray("models");
                    if (models != null && models.size() > 0) {
                        var result = new java.util.ArrayList<Model>();
                        for (var m : models) {
                            var name = m.getAsJsonObject().get("name").getAsString();
                            result.add(new Model(name, name, 32768, true));
                        }
                        return List.copyOf(result);
                    }
                }
            }
        } catch (Exception ignored) {}
        return MODELS;
    }

    @Override
    public AIResponse chat(List<Message> messages, String modelId) {
        var json = buildChatRequest(messages, modelId, false);
        var request = new Request.Builder()
            .url(baseUrl + "/api/chat")
            .post(RequestBody.create(json, MediaType.parse("application/json")))
            .build();

        try (var response = client.newCall(request).execute()) {
            var body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("Ollama API error: " + response.code());
            }
            return parseResponse(body.string(), modelId);
        } catch (Exception e) {
            throw new RuntimeException("Ollama chat failed", e);
        }
    }

    @Override
    public void chatStreaming(
        List<Message> messages, String modelId,
        Consumer<String> onChunk, Runnable onComplete, Consumer<Exception> onError
    ) {
        CompletableFuture.runAsync(() -> {
            try {
                var json = buildChatRequest(messages, modelId, true);
                var request = new Request.Builder()
                    .url(baseUrl + "/api/chat")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

                var response = client.newCall(request).execute();
                var body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IOException("Ollama API error: " + response.code());
                }

                var reader = body.charStream();
                var buf = new char[4096];
                int read;
                while ((read = reader.read(buf, 0, buf.length)) != -1) {
                    var data = new String(buf, 0, read);
                    for (var line : data.split("\n")) {
                        if (line.isBlank()) continue;
                        try {
                            var obj = gson.fromJson(line, JsonObject.class);
                            if (obj != null && obj.get("done") != null) {
                                if (obj.get("done").getAsBoolean()) {
                                    onComplete.run();
                                }
                                if (obj.get("message") != null) {
                                    var content = obj.getAsJsonObject("message").get("content");
                                    if (content != null) {
                                        onChunk.accept(content.getAsString());
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                onComplete.run();
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    private String buildChatRequest(List<Message> messages, String modelId, boolean stream) {
        var root = new JsonObject();
        root.addProperty("model", modelId);
        root.addProperty("stream", stream);

        var msgs = new com.google.gson.JsonArray();
        for (var msg : messages) {
            var obj = new JsonObject();
            obj.addProperty("role", msg.role());
            obj.addProperty("content", msg.content());
            msgs.add(obj);
        }
        root.add("messages", msgs);
        return gson.toJson(root);
    }

    private AIResponse parseResponse(String responseBody, String modelId) {
        var obj = gson.fromJson(responseBody, JsonObject.class);
        var msg = obj.getAsJsonObject("message");
        var content = msg.get("content").getAsString();

        int promptTokens = obj.has("prompt_eval_count") ? obj.get("prompt_eval_count").getAsInt() : 0;
        int completionTokens = obj.has("eval_count") ? obj.get("eval_count").getAsInt() : 0;

        return new AIResponse(content, promptTokens, completionTokens, modelId);
    }

    @Override
    public double costPerPromptToken() {
        return 0; // Local – kostenlos
    }

    @Override
    public double costPerCompletionToken() {
        return 0;
    }
}
