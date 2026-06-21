package dev.lu212.bv.ai.memory;

import dev.lu212.bv.ai.Message;
import dev.lu212.bv.ai.ProviderManager;
import dev.lu212.bv.db.MemoryRepository;
import dev.lu212.bv.db.MessageRepository;
import dev.lu212.bv.indexer.TodoScanner;
import dev.lu212.bv.i18n.Messages;
import dev.lu212.bv.util.FileUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MemoryManager {

    private static final int MAX_MEMORIES = 50;
    private static final int MAX_MESSAGES_BEFORE_COMPRESSION = 30;
    private static final int RECENT_MESSAGES_TO_KEEP = 10;

    private final MemoryRepository memoryRepo;
    private final MessageRepository messageRepo;
    private final ProviderManager providerManager;
    private final String projectPath;
    private String projectIndexSummary = "";
    private String classContext = "";
    private String todoSummary = "";

    public MemoryManager(MemoryRepository memoryRepo, MessageRepository messageRepo, ProviderManager providerManager, String projectPath) {
        this.memoryRepo = memoryRepo;
        this.messageRepo = messageRepo;
        this.providerManager = providerManager;
        this.projectPath = projectPath;
    }

    public void setProjectIndexSummary(String summary) {
        this.projectIndexSummary = summary != null ? summary : "";
    }

    public void setClassContext(String context) {
        this.classContext = context != null ? context : "";
    }

    public void setTodoSummary(String summary) {
        this.todoSummary = summary != null ? summary : "";
    }

    public void scanAndSetTodos() {
        var todos = TodoScanner.scanProject(Path.of(projectPath));
        this.todoSummary = TodoScanner.formatTodos(todos);
    }

    public String buildContextWithMemories(String projectPath, String goal) {
        var sb = new StringBuilder();
            sb.append(Messages.get("prompt.main")).append("\n");
            sb.append(Messages.get("prompt.goal")).append("\n\n");
        if (goal != null && !goal.isBlank()) {
            sb.append("## Aktuelles Ziel\n").append(goal).append("\n\n");
        }

        if (!projectIndexSummary.isBlank()) {
            sb.append(projectIndexSummary).append("\n");
        }

        if (!classContext.isBlank()) {
            sb.append(classContext).append("\n");
        }

        if (!todoSummary.isBlank()) {
            sb.append(todoSummary).append("\n");
        }

        var structure = FileUtils.getProjectStructure(Path.of(projectPath));
        if (!structure.isBlank()) {
            sb.append("## Projektstruktur\n```\n").append(structure).append("```\n\n");
        }

        var memories = memoryRepo.getMemories(projectPath, MAX_MEMORIES);
        if (!memories.isEmpty()) {
            sb.append("## Gespeicherte Erinnerungen (aus vorherigen Sessions)\n");
            for (var mem : memories) {
                sb.append("- ").append(mem.summary()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public List<Message> buildMessageList(String projectPath, String goal, String diff, String userMessage) {
        var messages = new ArrayList<Message>();

        var systemContext = buildContextWithMemories(projectPath, goal);
        if (diff != null && !diff.isBlank()) {
            systemContext += "## Aktuelle Code-Änderungen\n```diff\n" + diff + "\n```\n";
        }
        messages.add(new Message("system", systemContext, 0));

        var recent = messageRepo.getRecent(projectPath, RECENT_MESSAGES_TO_KEEP);
        messages.addAll(recent);

        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(new Message("user", userMessage, 0));
        }

        return messages;
    }

    public void saveMessage(String projectPath, String role, String content, int tokens) {
        messageRepo.save(projectPath, role, content, tokens);

        if (messageRepo.countMessages(projectPath) > MAX_MESSAGES_BEFORE_COMPRESSION) {
            compressOldMessages(projectPath);
        }
    }

    private void compressOldMessages(String projectPath) {
        var allMessages = messageRepo.getRecent(projectPath, MAX_MESSAGES_BEFORE_COMPRESSION + 1);
        if (allMessages.size() <= RECENT_MESSAGES_TO_KEEP) return;

        var toCompress = allMessages.subList(0, allMessages.size() - RECENT_MESSAGES_TO_KEEP);
        var summary = compressMessages(toCompress);

        if (summary != null) {
            memoryRepo.save(projectPath, summary, summary.length() / 4);
            memoryRepo.deleteOldest(projectPath, MAX_MEMORIES);
        }

        messageRepo.deleteOlderThan(projectPath, RECENT_MESSAGES_TO_KEEP);
    }

    private String compressMessages(List<Message> messages) {
        var provider = providerManager.activeProvider();
        var modelId = providerManager.activeModelId();
        if (provider == null || modelId == null) return null;
        try {
            var sysMsg = new Message("system",
                "Fasse die folgende Unterhaltung in 2-3 Sätzen zusammen. " +
                "Was wurde besprochen? Welche Entscheidungen getroffen? Was ist der aktuelle Stand?");
            var chatMessages = new ArrayList<Message>();
            chatMessages.add(sysMsg);
            chatMessages.addAll(messages);
            var response = provider.chat(chatMessages, modelId);
            return response.content();
        } catch (Exception e) {
            System.err.println("Memory compression failed: " + e.getMessage());
            return null;
        }
    }

    public int getMemoryCount(String projectPath) {
        return memoryRepo.countMemories(projectPath);
    }

    public int getMessageCount(String projectPath) {
        return messageRepo.countMessages(projectPath);
    }

    public void clearSession(String projectPath) {
        messageRepo.clear(projectPath);
    }
}
