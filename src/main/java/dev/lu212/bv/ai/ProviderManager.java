package dev.lu212.bv.ai;

import dev.lu212.bv.db.ProviderConfigRepository;

import java.util.*;

public final class ProviderManager {

    private final Map<String, AIProvider> providers = new LinkedHashMap<>();
    private final ProviderConfigRepository configRepo;

    private AIProvider activeProvider;
    private String activeModelId;

    public ProviderManager(ProviderConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    public void register(AIProvider provider) {
        providers.put(provider.getName().toLowerCase(), provider);
    }

    public AIProvider getProvider(String name) {
        return providers.get(name.toLowerCase());
    }

    public List<AIProvider> getRegisteredProviders() {
        return List.copyOf(providers.values());
    }

    public List<String> getProviderNames() {
        return List.copyOf(providers.keySet());
    }

    public boolean setActiveProvider(String name) {
        var p = getProvider(name);
        if (p != null) {
            activeProvider = p;
            configRepo.setActive(name);
            return true;
        }
        return false;
    }

    public boolean setActiveModel(String modelId) {
        if (activeProvider == null) return false;
        var validModels = activeProvider.getModels();
        if (validModels.stream().anyMatch(m -> m.id().equals(modelId))) {
            activeModelId = modelId;
            return true;
        }
        return false;
    }

    public AIProvider activeProvider() {
        return activeProvider;
    }

    public String activeModelId() {
        return activeModelId;
    }

    public Optional<String> getApiKey(String providerName) {
        return configRepo.getApiKey(providerName);
    }

    public void saveApiKey(String providerName, String apiKey) {
        configRepo.save(providerName, apiKey, null);
    }

    public AIResponse chat(List<Message> messages) {
        if (activeProvider == null) {
            throw new IllegalStateException("No active provider set");
        }
        return activeProvider.chat(messages, activeModelId);
    }

    public void chatStreaming(
        List<Message> messages,
        java.util.function.Consumer<String> onChunk,
        Runnable onComplete,
        java.util.function.Consumer<Exception> onError
    ) {
        if (activeProvider == null) {
            onError.accept(new IllegalStateException("No active provider set"));
            return;
        }
        activeProvider.chatStreaming(messages, activeModelId, onChunk, onComplete, onError);
    }
}
