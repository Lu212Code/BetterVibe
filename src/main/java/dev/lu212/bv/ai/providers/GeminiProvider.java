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

public final class GeminiProvider implements AIProvider {

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final List<Model> MODELS = List.of(
        new Model("gemini-3.5-flash", "Gemini 3.5 Flash", 1048576, true),
        new Model("gemini-3-flash-preview", "Gemini 3 Flash (Preview)", 1048576, true),
        new Model("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", 1048576, true),
        new Model("gemini-3.1-pro-preview", "Gemini 3.1 Pro (Preview)", 2097152, true)
    );

    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private final String apiKey;
    private final String baseUrl;

    public GeminiProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public GeminiProvider(String apiKey, String baseUrl) {
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
        return "gemini";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("your-gemini-key");
    }

    @Override
    public List<Model> getModels() {
        return MODELS;
    }

    @Override
    public AIResponse chat(List<Message> messages, String modelId) {
        var json = buildChatRequest(messages);
        var request = new Request.Builder()
            .url(baseUrl + "/models/" + modelId + ":generateContent?key=" + apiKey)
            .post(RequestBody.create(json, MediaType.parse("application/json")))
            .build();

        try (var response = client.newCall(request).execute()) {
            var body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("Gemini API error: " + response.code());
            }
            return parseResponse(body.string(), modelId);
        } catch (Exception e) {
            throw new RuntimeException("Gemini chat failed", e);
        }
    }

    @Override
    public void chatStreaming(
        List<Message> messages, String modelId,
        Consumer<String> onChunk, Runnable onComplete, Consumer<Exception> onError
    ) {
        CompletableFuture.runAsync(() -> {
            try {
                var json = buildChatRequest(messages);
                var request = new Request.Builder()
                    .url(baseUrl + "/models/" + modelId + ":streamGenerateContent?alt=sse&key=" + apiKey)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

                var response = client.newCall(request).execute();
                var body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IOException("Gemini API error: " + response.code());
                }

                var reader = body.charStream();
                var buf = new char[4096];
                int read;
                while ((read = reader.read(buf, 0, buf.length)) != -1) {
                    var data = new String(buf, 0, read);
                    for (var line : data.split("\n")) {
                        if (line.startsWith("data: ")) {
                            var content = line.substring(6);
                            if ("[DONE]".equals(content.trim())) continue;
                            try {
                                var jsonObj = gson.fromJson(content, JsonObject.class);
                                if (jsonObj != null) {
                                    var candidates = jsonObj.getAsJsonArray("candidates");
                                    if (candidates != null && candidates.size() > 0) {
                                        var text = candidates.get(0).getAsJsonObject()
                                            .getAsJsonObject("content")
                                            .getAsJsonArray("parts")
                                            .get(0).getAsJsonObject();
                                        if (text.get("text") != null) {
                                            onChunk.accept(text.get("text").getAsString());
                                        }
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

    private String buildChatRequest(List<Message> messages) {
        var root = new JsonObject();
        var contents = new com.google.gson.JsonArray();
        for (var msg : messages) {
            var part = new JsonObject();
            part.addProperty("text", msg.content());

            var parts = new com.google.gson.JsonArray();
            parts.add(part);

            var content = new JsonObject();
            content.addProperty("role", "user".equals(msg.role()) ? "user" : "model");
            content.add("parts", parts);
            contents.add(content);
        }
        root.add("contents", contents);

        var safetySettings = new com.google.gson.JsonArray();
        for (var category : List.of(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        )) {
            var setting = new JsonObject();
            setting.addProperty("category", category);
            setting.addProperty("threshold", "BLOCK_NONE");
            safetySettings.add(setting);
        }
        root.add("safetySettings", safetySettings);

        return gson.toJson(root);
    }

    private AIResponse parseResponse(String responseBody, String modelId) {
        var obj = gson.fromJson(responseBody, JsonObject.class);
        var candidates = obj.getAsJsonArray("candidates");
        var text = candidates.get(0).getAsJsonObject()
            .getAsJsonObject("content")
            .getAsJsonArray("parts")
            .get(0).getAsJsonObject()
            .get("text").getAsString();

        var usage = obj.getAsJsonObject("usageMetadata");
        int promptTokens = usage.get("promptTokenCount").getAsInt();
        int completionTokens = usage.get("candidatesTokenCount").getAsInt();

        return new AIResponse(text, promptTokens, completionTokens, modelId);
    }

    @Override
    public double costPerPromptToken() {
        return 0.0000005; // $0.50/1M tokens (Gemini 3 Flash pricing)
    }

    @Override
    public double costPerCompletionToken() {
        return 0.000003; // $3.00/1M tokens
    }
}
