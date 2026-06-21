package dev.lu212.bv.ai.providers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lu212.bv.ai.AIProvider;
import dev.lu212.bv.ai.AIResponse;
import dev.lu212.bv.ai.Message;
import dev.lu212.bv.ai.Model;

import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class OpenAIProvider implements AIProvider {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final List<Model> MODELS = List.of(
        new Model("gpt-4o", "GPT-4o", 128000, true),
        new Model("gpt-4o-mini", "GPT-4o Mini", 128000, true),
        new Model("gpt-4-turbo", "GPT-4 Turbo", 128000, true),
        new Model("gpt-3.5-turbo", "GPT-3.5 Turbo", 16385, true)
    );

    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private final String apiKey;
    private final String baseUrl;

    public OpenAIProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public OpenAIProvider(String apiKey, String baseUrl) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String getName() {
        return "openai";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("sk-your-key-here");
    }

    @Override
    public List<Model> getModels() {
        return MODELS;
    }

    @Override
    public AIResponse chat(List<Message> messages, String modelId) {
        var json = buildChatRequest(messages, modelId, false);
        var request = new Request.Builder()
            .url(baseUrl + "/chat/completions")
            .post(RequestBody.create(json, MediaType.parse("application/json")))
            .addHeader("Authorization", "Bearer " + apiKey)
            .build();

        try (var response = client.newCall(request).execute()) {
            var body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("OpenAI API error: " + response.code() + " " + body);
            }
            return parseResponse(body.string(), modelId);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI chat failed", e);
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
                    .url(baseUrl + "/chat/completions")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

                var response = client.newCall(request).execute();
                var body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IOException("OpenAI API error: " + response.code());
                }

                var reader = body.charStream();
                var buf = new char[4096];
                int read;
                while ((read = reader.read(buf, 0, buf.length)) != -1) {
                    var data = new String(buf, 0, read);
                    for (var line : data.split("\n")) {
                        if (line.startsWith("data: ")) {
                            var content = line.substring(6);
                            if ("[DONE]".equals(content)) continue;
                            try {
                                var jsonObj = gson.fromJson(content, JsonObject.class);
                                if (jsonObj != null) {
                                    var delta = jsonObj
                                        .getAsJsonArray("choices")
                                        .get(0).getAsJsonObject()
                                        .getAsJsonObject("delta");
                                    if (delta != null && delta.get("content") != null) {
                                        onChunk.accept(delta.get("content").getAsString());
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
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

        var arr = new JsonArray();
        for (var msg : messages) {
            var obj = new JsonObject();
            obj.addProperty("role", msg.role());
            obj.addProperty("content", msg.content());
            arr.add(obj);
        }
        root.add("messages", arr);
        return gson.toJson(root);
    }

    private AIResponse parseResponse(String responseBody, String modelId) {
        var obj = gson.fromJson(responseBody, JsonObject.class);
        var choice = obj.getAsJsonArray("choices").get(0).getAsJsonObject();
        var content = choice.getAsJsonObject("message").get("content").getAsString();

        var usage = obj.getAsJsonObject("usage");
        int promptTokens = usage.get("prompt_tokens").getAsInt();
        int completionTokens = usage.get("completion_tokens").getAsInt();

        return new AIResponse(content, promptTokens, completionTokens, modelId);
    }

    @Override
    public double costPerPromptToken() {
        return 0.0000025; // $2.50/1M tokens for GPT-4o
    }

    @Override
    public double costPerCompletionToken() {
        return 0.00001; // $10/1M tokens for GPT-4o
    }
}
