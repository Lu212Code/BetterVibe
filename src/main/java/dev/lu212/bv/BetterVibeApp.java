package dev.lu212.bv;

import dev.lu212.bv.ai.ProviderManager;
import dev.lu212.bv.ai.expense.ExpenseTracker;
import dev.lu212.bv.i18n.Messages;
import dev.lu212.bv.ai.memory.MemoryManager;
import dev.lu212.bv.ai.providers.*;
import dev.lu212.bv.config.AppConfig;
import dev.lu212.bv.db.*;
import dev.lu212.bv.indexer.IndexingDialog;
import dev.lu212.bv.indexer.ProjectIndexer;
import dev.lu212.bv.indexer.TodoScanner;
import dev.lu212.bv.input.GlobalHotkeyManager;
import dev.lu212.bv.input.HotkeyAction;
import dev.lu212.bv.input.HotkeyBinding;
import dev.lu212.bv.ui.AppMode;
import dev.lu212.bv.ui.cli.CLIApplication;
import dev.lu212.bv.ui.overlay.OverlayApplication;
import dev.lu212.bv.util.GitUtils;
import dev.lu212.bv.watch.FileWatcher;

import javafx.application.Application;

import dev.lu212.bv.util.TokenCounter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BetterVibeApp {

    private static BetterVibeApp instance;

    private final AppMode mode;
    private final String projectPath;
    private final AppConfig appConfig;
    private final DatabaseManager db;
    private final GoalRepository goalRepo;
    private final MessageRepository messageRepo;
    private final MemoryRepository memoryRepo;
    private final ExpenseRepository expenseRepo;
    private final ProviderConfigRepository providerConfigRepo;
    private final HotkeyBindingRepository bindingRepo;
    private final ProjectIndexRepository projectIndexRepo;
    private final ProviderManager providerManager;
    private final ExpenseTracker expenseTracker;
    private final MemoryManager memoryManager;
    private final FileWatcher fileWatcher;
    private final GlobalHotkeyManager hotkeyManager;
    private ProjectIndexer projectIndexer;
    private volatile Map<HotkeyAction, HotkeyBinding> bindings;

    private volatile OverlayApplication.OverlayController overlayController;
    private volatile long lastHotkeyActionTime;

    public void setOverlayController(OverlayApplication.OverlayController c) {
        this.overlayController = c;
    }
    private CLIApplication cliApp;
    private Thread cliThread;
    private final AtomicBoolean autoReviewRunning = new AtomicBoolean(false);
    private Thread autoReviewThread;

    private BetterVibeApp(String[] args) {
        this.appConfig = new AppConfig();
        Messages.init(appConfig.get("lang", "en"));
        this.mode = parseMode(args);
        this.projectPath = parseProjectPath(args);
        this.db = new DatabaseManager();
        var conn = db.getConnection();
        this.goalRepo = new GoalRepository(conn);
        this.messageRepo = new MessageRepository(conn);
        this.memoryRepo = new MemoryRepository(conn);
        this.expenseRepo = new ExpenseRepository(conn);
        this.providerConfigRepo = new ProviderConfigRepository(conn);
        this.bindingRepo = new HotkeyBindingRepository(conn);
        this.projectIndexRepo = new ProjectIndexRepository(conn);

        this.providerManager = new ProviderManager(providerConfigRepo);
        this.expenseTracker = new ExpenseTracker(expenseRepo);

        initProviders(args);

        this.memoryManager = new MemoryManager(
            memoryRepo, messageRepo, providerManager, projectPath
        );

        this.fileWatcher = new FileWatcher(Path.of(projectPath));
        this.hotkeyManager = new GlobalHotkeyManager(this::showError);
        initHotkeys();
    }

    public static void main(String[] args) {
        instance = new BetterVibeApp(args);
        instance.start();
    }

    public static BetterVibeApp getInstance() {
        return instance;
    }

    // ============================================================
    // Initialization
    // ============================================================

    private AppMode parseMode(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--mode".equals(args[i]) && i + 1 < args.length) {
                return AppMode.fromString(args[i + 1]);
            }
        }
        return AppMode.fromString(appConfig.get("mode", "overlay"));
    }

    private String parseProjectPath(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--project".equals(args[i]) && i + 1 < args.length) {
                return Path.of(args[i + 1]).toAbsolutePath().normalize().toString();
            }
        }
        return appConfig.get("project_path", AppDefaults.DEFAULT_PROJECT_PATH);
    }

    private void initProviders(String[] args) {
        var openaiKey = findArg(args, "--openai-key");
        var geminiKey = findArg(args, "--gemini-key");
        var ollamaUrl = findArg(args, "--ollama-url");

        if (openaiKey == null) openaiKey = providerConfigRepo.getApiKey("openai").orElse("");
        if (geminiKey == null) geminiKey = providerConfigRepo.getApiKey("gemini").orElse("");
        if (ollamaUrl == null) ollamaUrl = providerConfigRepo.getBaseUrl("ollama").orElse("");

        // Always register all providers – isAvailable() determines usability
        providerManager.register(new OpenAIProvider(openaiKey != null ? openaiKey : ""));
        providerManager.register(new GeminiProvider(geminiKey != null ? geminiKey : ""));
        providerManager.register(new OllamaProvider(ollamaUrl != null ? ollamaUrl : ""));

        var stored = goalRepo.getProvider(projectPath);
        stored.ifPresent(pm -> {
            var parts = pm.split(":", 2);
            if (parts.length == 2) {
                providerManager.setActiveProvider(parts[0]);
                providerManager.setActiveModel(parts[1]);
            }
        });
    }

    private void registerProvider(String name, String apiKey) {
        switch (name.toLowerCase()) {
            case "openai" -> providerManager.register(new OpenAIProvider(apiKey));
            case "gemini" -> providerManager.register(new GeminiProvider(apiKey));
        }
    }

    private String findArg(String[] args, String key) {
        for (int i = 0; i < args.length; i++) {
            if (key.equals(args[i]) && i + 1 < args.length) return args[i + 1];
        }
        return null;
    }

    private void initHotkeys() {
        bindings = bindingRepo.loadAll();
        hotkeyManager.loadBindings(bindings);
        hotkeyManager.onAction(HotkeyAction.REVIEW_CODE, this::handleReview);
    }

    // ============================================================
    // Start
    // ============================================================

    private void start() {
        hotkeyManager.register();

        if (mode == AppMode.OVERLAY) {
            if (!hotkeyManager.isRegistered()) {
                System.out.println("\u26a0  Globale Hotkeys nicht verf\u00fcgbar. Verwende JavaFX-interne Shortcuts.");
                System.out.println("   Fokussiere das Overlay-Fenster f\u00fcr Tastenkombinationen.");
            }
            System.out.println("Starte Overlay...");
            BiConsumer<String, String> keySaver = (name, key) -> {
                registerProvider(name, key);
                providerManager.setActiveProvider(name);
            };
            OverlayApplication.initStatic(
                providerManager, expenseTracker, goalRepo, providerConfigRepo,
                bindingRepo, messageRepo, fileWatcher, appConfig,
                projectPath, this::handleUserMessage, this::handleHotkeyAction,
                () -> {
                    var m = goalRepo.getMode(projectPath).orElse("manual");
                    setAutoReview("auto".equals(m));
                },
                keySaver
            );
            Application.launch(OverlayApplication.class);
        } else {
            if (!hotkeyManager.isRegistered()) {
                System.out.println("\u26a0  Globale Hotkeys nicht verf\u00fcgbar.");
                System.out.println("   Die CLI l\u00e4uft trotzdem \u2013 verwende die Eingabezeile.");
            }
            cliApp = new CLIApplication(
                providerManager, expenseTracker, goalRepo, bindingRepo,
                projectPath, this::handleUserMessage, this::handleHotkeyAction
            );
            cliApp.setBindings(bindings);
            cliApp.setOnModeChanged(() -> {
                var m = goalRepo.getMode(projectPath).orElse("manual");
                setAutoReview("auto".equals(m));
            });
            cliApp.setOnBindingsChanged(this::reloadBindings);
            cliApp.setOnProviderKeySaved((name, key) -> {
                registerProvider(name, key);
                providerManager.setActiveProvider(name);
            });
            cliThread = new Thread(cliApp, "CLI");
            cliThread.setDaemon(false);
            cliThread.start();
        }

        var modeStr = goalRepo.getMode(projectPath).orElse("manual");
        if ("auto".equals(modeStr)) {
            setAutoReview(true);
        }

        if (mode == AppMode.OVERLAY) {
            checkProjectIndexing();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    // ============================================================
    // Core Actions
    // ============================================================

    private List<String> extractChangedFiles(String diff) {
        var files = new ArrayList<String>();
        if (diff == null || diff.isBlank()) return files;
        var pattern = java.util.regex.Pattern.compile(
            "^diff --git a/(?:.*?) b/(.*)$", java.util.regex.Pattern.MULTILINE
        );
        var matcher = pattern.matcher(diff);
        while (matcher.find()) {
            var file = matcher.group(1);
            if (file != null && !file.isBlank() && !files.contains(file)) {
                files.add(file);
            }
        }
        if (files.isEmpty()) {
            var linePattern = java.util.regex.Pattern.compile(
                "^\\[.\\] (.+)$", java.util.regex.Pattern.MULTILINE
            );
            var lineMatcher = linePattern.matcher(diff);
            while (lineMatcher.find()) {
                var file = lineMatcher.group(1);
                if (file != null && !file.isBlank() && !files.contains(file)) {
                    files.add(file);
                }
            }
        }
        return files;
    }

    private boolean hasMeaningfulChanges(String diff) {
        if (diff == null || diff.isBlank()) return false;
        var lines = diff.split("\n");
        int meaningful = 0;
        boolean inCodeBlock = false;
        for (var line : lines) {
            var stripped = line.strip();
            if (stripped.equals("```")) { inCodeBlock = !inCodeBlock; continue; }
            if (inCodeBlock) { meaningful++; continue; }
            if (stripped.startsWith("diff --git") || stripped.startsWith("---") || stripped.startsWith("+++")
                || stripped.startsWith("@@") || stripped.startsWith("index")
                || stripped.startsWith("new file") || stripped.startsWith("deleted"))
                continue;
            if (stripped.startsWith("[") || stripped.startsWith("```")) continue;
            if (stripped.startsWith("+") || stripped.startsWith("-")) {
                var code = stripped.substring(1).strip();
                if (code.isEmpty() || code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")
                    || code.startsWith("import ") || code.startsWith("package "))
                    continue;
                meaningful++;
            }
        }
        return meaningful > 0;
    }

    private String getDiffContent() {
        var gitDir = Path.of(projectPath);
        if (GitUtils.isGitRepo(gitDir)) {
            var result = GitUtils.getWorkingTreeDiff(gitDir);
            if (result != null && !result.diff().isBlank()) {
                return result.diff();
            }
        }
        return fileWatcher.getDiffSinceLastClear();
    }

    private String enhanceDiffWithContext(String diff) {
        if (diff == null || diff.isBlank()) return diff;
        var changedFiles = extractChangedFiles(diff);
        var classCtx = projectIndexRepo.buildClassContextForFiles(projectPath, changedFiles);
        if (classCtx.isBlank()) return diff;
        return classCtx + diff;
    }

    private void flashBinding(HotkeyAction action, boolean success) {
        if (mode == AppMode.OVERLAY && overlayController != null) {
            overlayController.flashBinding(action, success);
        }
    }

    private void handleReview() {
        var now = System.currentTimeMillis();
        if (now - lastHotkeyActionTime < 300) return;
        lastHotkeyActionTime = now;
        var provider = providerManager.activeProvider();
        if (provider == null) {
            flashBinding(HotkeyAction.REVIEW_CODE, false);
            notifyUser("\u26a0 Kein Provider aktiv", "Konfiguriere einen Provider zuerst.", "#ff6644");
            setTaskStatus("Kein Provider", false, false);
            return;
        }

        var diff = getDiffContent();
        if (diff.isBlank()) {
            flashBinding(HotkeyAction.REVIEW_CODE, false);
            notifyUser(Messages.get("notify.review.started"), Messages.get("notify.review.empty"), "#88aaff");
            setTaskStatus("Keine \u00c4nderungen", false, false);
            return;
        }

        if (!hasMeaningfulChanges(diff)) {
            flashBinding(HotkeyAction.REVIEW_CODE, false);
            notifyUser(Messages.get("notify.review.started"), "Nur triviale \u00c4nderungen (Kommentare, Whitespace, Imports) \u2013 \u00fcbersprungen.", "#88aaff");
            fileWatcher.getAndClearChanges();
            setTaskStatus("Nur trivial", false, false);
            return;
        }

        setTaskStatus("Review...", true, false);

        var enhancedDiff = enhanceDiffWithContext(diff);
        memoryManager.setClassContext(projectIndexRepo.buildClassContextForFiles(projectPath, extractChangedFiles(diff)));
        memoryManager.scanAndSetTodos();

        var goal = goalRepo.getGoal(projectPath).orElse("");
        var messages = memoryManager.buildMessageList(projectPath, goal, enhancedDiff,
            "Sieh dir folgende Code-\u00c4nderungen an. " +
            "Erkl\u00e4re in 2-3 S\u00e4tzen, was die \u00c4nderungen bewirken und ob es Probleme gibt. " +
            "Antworte kurz auf Deutsch."
        );

        memoryManager.setClassContext("");

        fileWatcher.getAndClearChanges();

        var fileCount = extractChangedFiles(diff).size();
        flashBinding(HotkeyAction.REVIEW_CODE, true);
            notifyUser(Messages.get("notify.review.started"), "Analysiere \u00c4nderungen... (" + fileCount + " Dateien)", "#88aaff");

        var modelId = providerManager.activeModelId();

        if (mode == AppMode.CLI && cliApp != null) {
            cliApp.startStream("KI Review", "#66ff88");
            provider.chatStreaming(messages, modelId,
                chunk -> cliApp.appendStream(chunk),
                () -> {
                    var content = cliApp.finishStream();
                    var tokens = TokenCounter.estimateTokens(content);
                    var response = new dev.lu212.bv.ai.AIResponse(content, tokens, tokens, modelId);
                    expenseTracker.track(provider, response);
                    memoryManager.saveMessage(projectPath, "user",
                        "Review: " + fileCount + " Dateien ge\u00e4ndert", 0);
                    memoryManager.saveMessage(projectPath, "assistant", content, tokens);
                    updateStatus();
                    setTaskStatus("Review", false, true);
                },
                error -> {
                    cliApp.notifyStreamError(error.getMessage());
                    setTaskStatus("Review", false, false);
                }
            );
        } else {
            provider.chatAsync(messages, modelId)
                .thenAccept(response -> {
                    expenseTracker.track(provider, response);
                    memoryManager.saveMessage(projectPath, "user",
                        "Review: " + fileCount + " Dateien ge\u00e4ndert", 0);
                    memoryManager.saveMessage(projectPath, "assistant", response.content(), response.totalTokens());
                    notifyPersistent("\ud83e\udd16 KI Review (" + response.model() + ")", response.content(), "#66ff88");
                    updateStatus();
                    setTaskStatus("Review", false, true);
                })
                .exceptionally(e -> {
                    notifyUser("\u274c Fehler beim Review", e.getMessage(), "#ff4444");
                    setTaskStatus("Review", false, false);
                    return null;
                });
        }
    }

    private void handleUserMessage(String text) {
        if (text == null || text.isBlank()) return;

        if (text.startsWith("/")) {
            handleSlashCommand(text);
            return;
        }

        var provider = providerManager.activeProvider();
        if (provider == null) {
            notifyUser(Messages.get("notify.noprovider.title"), Messages.get("notify.noprovider.msg"), "#ff6644");
            return;
        }

        var diff = fileWatcher.getDiffSinceLastClear();
        var enhancedDiff = enhanceDiffWithContext(diff);
        memoryManager.setClassContext(projectIndexRepo.buildClassContextForFiles(projectPath, extractChangedFiles(diff)));
        memoryManager.scanAndSetTodos();

        var goal = goalRepo.getGoal(projectPath).orElse("");
        var messages = memoryManager.buildMessageList(projectPath, goal, enhancedDiff, text);

        memoryManager.setClassContext("");

        var modelId = providerManager.activeModelId();
        setTaskStatus(Messages.get("overlay.status.chatting"), true, false);

        if (mode == AppMode.CLI && cliApp != null) {
            cliApp.startStream("KI", "#66ccff");
            provider.chatStreaming(messages, modelId,
                chunk -> cliApp.appendStream(chunk),
                () -> {
                    var content = extractAndApplyGoal(cliApp.finishStream());
                    var tokens = TokenCounter.estimateTokens(content);
                    var response = new dev.lu212.bv.ai.AIResponse(content, tokens, tokens, modelId);
                    expenseTracker.track(provider, response);
                    memoryManager.saveMessage(projectPath, "user", text, 0);
                    memoryManager.saveMessage(projectPath, "assistant", content, tokens);
                    updateStatus();
                    setTaskStatus(Messages.get("overlay.status.ok"), false, true);
                },
                error -> {
                    cliApp.notifyStreamError(error.getMessage());
                    setTaskStatus(Messages.get("overlay.status.error"), false, false);
                }
            );
        } else {
            provider.chatAsync(messages, modelId)
                .thenAccept(response -> {
                    expenseTracker.track(provider, response);
                    var content = extractAndApplyGoal(response.content());
                    memoryManager.saveMessage(projectPath, "user", text, 0);
                    memoryManager.saveMessage(projectPath, "assistant", content, response.totalTokens());
                    notifyPersistent("\ud83e\udd16 KI (" + response.model() + ")", content, "#66ccff");
                    updateStatus();
                    setTaskStatus(Messages.get("overlay.status.ok"), false, true);
                })
                .exceptionally(e -> {
                    notifyUser("\u274c Fehler", e.getMessage(), "#ff4444");
                    setTaskStatus(Messages.get("overlay.status.error"), false, false);
                    return null;
                });
        }
    }

    private String extractAndApplyGoal(String content) {
        var matcher = java.util.regex.Pattern.compile("\\[GOAL:\\s*(.+?)\\]").matcher(content);
        if (matcher.find()) {
            var goal = matcher.group(1).strip();
            if (!goal.isBlank()) {
                goalRepo.setGoal(projectPath, goal);
                notifyUser(Messages.get("notify.goal.updated.title"), goal, "#ffcc44");
            }
            return matcher.replaceAll("").strip();
        }
        return content;
    }

    private void handleSlashCommand(String text) {
        var parts = text.substring(1).strip().split("\\s+", 2);
        var cmd = parts[0].toLowerCase();
        var arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "review", "r" -> handleReview();
            case "status", "s" -> showStatus();
            case "goal", "g" -> {
                if (arg.isBlank()) {
                    var existing = goalRepo.getGoal(projectPath).orElse("");
                    notifyUser(Messages.get("notify.goal.current.title"), existing.isEmpty() ? Messages.get("notify.goal.none") : existing, "#ffcc44");
                } else {
                    goalRepo.setGoal(projectPath, arg);
                    notifyUser(Messages.get("notify.goal.saved", arg), "Gespeichert: " + arg, "#ffcc44");
                }
            }
            case "config", "c" -> {
                if (arg.equals("bind")) {
                    handleConfigBind();
                } else if (arg.startsWith("bind ")) {
                    handleConfigBindChange(arg.substring(5).strip());
                } else if (mode == AppMode.OVERLAY && overlayController != null) {
                    overlayController.showConfig();
                } else {
                    showConfigStatus();
                }
            }
            case "todos", "t" -> showTodos();
            case "reindex", "ri" -> reindexProject();
            case "help", "h" -> showHelp();
            case "clear" -> {
                memoryManager.clearSession(projectPath);
                notifyUser(Messages.get("notify.session.cleared.title"), Messages.get("notify.session.cleared.msg"), "#88aaff");
            }
            default -> notifyUser(Messages.get("notify.unknown.title"), Messages.get("notify.unknown.msg"), "#ff6644");
        }
    }

    private void handleConfigBind() {
        var sb = new StringBuilder();
        sb.append("Aktuelle Tastenk\u00fcrzel:\n");
        for (var entry : bindings.entrySet()) {
            var action = entry.getKey();
            var binding = entry.getValue();
            sb.append("  ").append(binding.displayString(action))
              .append("  \u2013 ").append(action.description()).append("\n");
        }
        sb.append("\n\u00c4ndern mit: /config bind <aktion> <taste>\n");
        sb.append("Aktionen: review, toggle\n");
        sb.append("Tasten: A-Z, 0-9, F1-F12, SPACE, ENTER, TAB, ESCAPE\n");
        sb.append("Beispiel: /config bind review F5\n");
        notifyPersistent("\u2328 Tastenk\u00fcrzel", sb.toString().strip(), "#88aaff");
    }

    private void handleConfigBindChange(String arg) {
        var parts = arg.split("\\s+", 2);
        if (parts.length < 2) {
            notifyUser("\u26a0 Syntax", "/config bind <aktion> <taste>\nBeispiel: /config bind review F5", "#ff6644");
            return;
        }
        var actionName = parts[0].toLowerCase();
        var keyName = parts[1].toUpperCase();

        HotkeyAction action = null;
        for (var a : HotkeyAction.values()) {
            if (a.name().toLowerCase().replace("_", "").equals(actionName)
                || a.name().toLowerCase().equals(actionName)) {
                action = a;
                break;
            }
        }
        if (action == null) {
            notifyUser("\u26a0 Unbekannte Aktion", "Verf\u00fcgbar: review, toggle", "#ff6644");
            return;
        }

        var keyCode = HotkeyAction.keyCodeForName(keyName);
        if (keyCode < 0) {
            notifyUser("\u26a0 Unbekannte Taste", keyName, "#ff6644");
            return;
        }

        var binding = new HotkeyBinding(keyCode, keyName);
        bindingRepo.save(action, binding);
        bindings.put(action, binding);
        hotkeyManager.rebind(action, binding);
        notifyUser("\u2328 Tastenk\u00fcrzel", "Gespeichert: " + binding.displayString(action) + " \u2013 " + action.description(), "#88aaff");
    }

    private void reloadBindings() {
        bindings = bindingRepo.loadAll();
        hotkeyManager.loadBindings(bindings);
    }

    private void showStatus() {
        var p = providerManager.activeProvider();
        var m = providerManager.activeModelId();
        var summary = expenseTracker.getSummary();
        var goal = goalRepo.getGoal(projectPath).orElse("(kein)");
        var modeStr = goalRepo.getMode(projectPath).orElse("manual");
        var msg = """
            Provider: %s
            Modell:   %s
            Modus:    %s
            Kosten:   %s
            Calls:    %d
            Tokens:   %d Prompt / %d Completion
            Ziel:     %s
            """.formatted(
                p != null ? p.getName() : "\u274c",
                m != null ? m : "\u274c",
                modeStr,
                expenseTracker.getTotalCostFormatted(),
                summary.totalCalls(),
                summary.promptTokens(), summary.completionTokens(),
                goal
            );
        notifyPersistent("\ud83d\udcca Status", msg.strip(), "#66ccff");
    }

    private void showConfigStatus() {
        var p = providerManager.activeProvider();
        var m = providerManager.activeModelId();
        var modeStr = goalRepo.getMode(projectPath).orElse("manual");
        notifyPersistent("\u2699 Konfiguration",
            "Verf\u00fcgbar: " + String.join(", ", providerManager.getProviderNames()) +
            "\nAktiv: " + (p != null ? p.getName() + "/" + (m != null ? m : "?") : "\u274c") +
            "\nModus: " + modeStr +
            "\n\n\u00c4ndern per: /config provider, /config key, /config mode, /config bind",
            "#66ccff");
    }

    private void showTodos() {
        var todos = TodoScanner.scanProject(Path.of(projectPath));
        if (todos.isEmpty()) {
            notifyPersistent("\ud83d\udcdd Aufgaben", "Keine TODO/FIXME/HACK-Kommentare gefunden.", "#88aaff");
            return;
        }
        var sb = new StringBuilder();
        sb.append(todos.size()).append(" Aufgaben gefunden:\n");
        var grouped = new java.util.LinkedHashMap<String, java.util.List<TodoScanner.TodoItem>>();
        for (var t : todos) {
            grouped.computeIfAbsent(t.tag(), k -> new java.util.ArrayList<>()).add(t);
        }
        for (var entry : grouped.entrySet()) {
            sb.append("[").append(entry.getKey()).append("] ").append(entry.getValue().size()).append("x\n");
        }
        sb.append("\n/details mit /todos all");
        notifyPersistent("\ud83d\udcdd Aufgaben", sb.toString(), "#ffcc44");
    }

    private void reindexProject() {
        notifyUser("\ud83d\udd0d Indexierung", "Starte Neu-Indexierung...", "#88aaff");
        setTaskStatus("Indiziere...", true, false);
        projectIndexRepo.clear(projectPath);
        var provider = providerManager.activeProvider();
        if (provider != null && provider.isAvailable()) {
            projectIndexer = new ProjectIndexer(projectIndexRepo, provider, providerManager.activeModelId(), Path.of(projectPath));
            new Thread(() -> projectIndexer.indexProject(
                p -> {},
                () -> {
                    memoryManager.setProjectIndexSummary(projectIndexRepo.buildIndexSummary(projectPath));
                    notifyUser("\ud83d\udd0d Indexierung", "Projekt neu indexiert (" +
                        projectIndexRepo.getClasses(projectPath).size() + " Klassen)", "#66ff88");
                    setTaskStatus("Indizierung", false, true);
                },
                e -> {
                    notifyUser("\u274c Fehler", e, "#ff4444");
                    setTaskStatus("Indizierung", false, false);
                }
            )).start();
        } else {
            projectIndexer = new ProjectIndexer(projectIndexRepo, null, null, Path.of(projectPath));
            new Thread(() -> projectIndexer.indexProjectBasic(
                p -> {},
                () -> {
                    memoryManager.setProjectIndexSummary(projectIndexRepo.buildIndexSummary(projectPath));
                    notifyUser("\ud83d\udd0d Indexierung", "Projekt neu indexiert (" +
                        projectIndexRepo.getClasses(projectPath).size() + " Klassen)", "#88aaff");
                    setTaskStatus("Indizierung", false, true);
                },
                e -> {
                    notifyUser("\u274c Fehler", e, "#ff4444");
                    setTaskStatus("Indizierung", false, false);
                }
            )).start();
        }
    }

    private void showHelp() {
        notifyPersistent(Messages.get("cli.help"),
            """
            /review, /r    \u2013 Code-\u00c4nderungen reviewen (mit Klassenkontext)
            /status, /s    \u2013 Status anzeigen
            /goal, /g ...  \u2013 Ziel setzen oder anzeigen
            /config, /c    \u2013 Provider/Modus konfigurieren
            /config bind   \u2013 Tastenk\u00fcrzel anzeigen/\u00e4ndern
            /todos, /t     \u2013 TODO/FIXME-Kommentare anzeigen
            /reindex, /ri  \u2013 Projekt neu indexieren
            /clear         \u2013 Session-Nachrichten l\u00f6schen
            /help, /h      \u2013 Diese Hilfe
            Alles andere   \u2013 Wird als Nachricht an die KI gesendet
            """ .strip(),
            "#88aaff");
    }

    private void handleHotkeyAction(HotkeyAction action) {
        switch (action) {
            case REVIEW_CODE -> handleReview();
            case TOGGLE_OVERLAY -> {}
        }
    }

    // ============================================================
    // Projekt-Indexierung
    // ============================================================

    private void checkProjectIndexing() {
        if (projectIndexRepo.isIndexed(projectPath)) {
            var classes = projectIndexRepo.getClasses(projectPath);
            if (!classes.isEmpty()) {
                memoryManager.setProjectIndexSummary(projectIndexRepo.buildIndexSummary(projectPath));
            }
            return;
        }

        var provider = providerManager.activeProvider();
        if (provider != null && provider.isAvailable()) {
            notifyUser("\ud83d\udd0d Indexierung", "Indiziere Projekt mit KI-Beschreibungen...", "#4a7fa5");
            indexProjectFull(null);
        } else {
            notifyUser("\ud83d\udd0d Indexierung", "Indiziere Projektstruktur...", "#4a7fa5");
            indexProjectBasic(null);
        }
    }

    private void indexProjectFull(IndexingDialog dialog) {
        var provider = providerManager.activeProvider();
        if (provider == null || !provider.isAvailable()) {
            indexProjectBasic(dialog);
            return;
        }
        setTaskStatus("Indiziere...", true, false);
        var modelId = providerManager.activeModelId();
        projectIndexer = new ProjectIndexer(projectIndexRepo, provider, modelId, Path.of(projectPath));
        new Thread(() -> {
            projectIndexer.indexProject(
                progress -> {
                    if (dialog != null) dialog.updateProgress(progress);
                },
                () -> {
                    if (dialog != null) dialog.showComplete(projectIndexRepo.getClasses(projectPath).size());
                    memoryManager.setProjectIndexSummary(projectIndexRepo.buildIndexSummary(projectPath));
                    notifyUser("\ud83d\udd0d Indexierung", "Projekt vollst\u00e4ndig indexiert!", "#66ff88");
                    setTaskStatus("Indizierung", false, true);
                },
                error -> {
                    notifyUser("\u274c Indexierung", error, "#ff4444");
                    setTaskStatus("Indizierung", false, false);
                }
            );
        }, "ProjectIndexer").start();
    }

    private void indexProjectBasic(IndexingDialog dialog) {
        setTaskStatus("Indiziere...", true, false);
        projectIndexer = new ProjectIndexer(projectIndexRepo, null, null, Path.of(projectPath));
        new Thread(() -> {
            projectIndexer.indexProjectBasic(
                progress -> {
                    if (dialog != null) dialog.updateProgress(progress);
                },
                () -> {
                    if (dialog != null) dialog.showComplete(projectIndexRepo.getClasses(projectPath).size());
                    memoryManager.setProjectIndexSummary(projectIndexRepo.buildIndexSummary(projectPath));
                    notifyUser("\ud83d\udd0d Indexierung", "Projektstruktur erfasst (" +
                        projectIndexRepo.getClasses(projectPath).size() + " Klassen)", "#88aaff");
                    setTaskStatus("Indizierung", false, true);
                },
                error -> {
                    notifyUser("\u274c Indexierung", error, "#ff4444");
                    setTaskStatus("Indizierung", false, false);
                }
            );
        }, "ProjectIndexer").start();
    }

    // ============================================================
    // Auto-Review Loop
    // ============================================================

    public void setAutoReview(boolean enabled) {
        if (enabled) {
            startAutoReviewLoop();
        } else {
            stopAutoReviewLoop();
        }
    }

    private synchronized void startAutoReviewLoop() {
        if (autoReviewRunning.getAndSet(true)) return;
        autoReviewThread = new Thread(() -> {
            while (autoReviewRunning.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30_000);
                    if (autoReviewRunning.get() && fileWatcher.hasChanges()) {
                        handleReview();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "AutoReview");
        autoReviewThread.setDaemon(true);
        autoReviewThread.start();
    }

    private synchronized void stopAutoReviewLoop() {
        autoReviewRunning.set(false);
        if (autoReviewThread != null) {
            autoReviewThread.interrupt();
            autoReviewThread = null;
        }
    }

    // ============================================================
    // UI Helpers
    // ============================================================

    private void notifyUser(String sender, String message, String color) {
        if (mode == AppMode.OVERLAY && overlayController != null) {
            overlayController.notify(sender, message, color);
        }
        if (mode == AppMode.CLI && cliApp != null) {
            cliApp.notify(sender, message, color);
        }
        System.out.println("[" + sender + "] " + message);
    }

    private void notifyPersistent(String sender, String message, String color) {
        if (mode == AppMode.OVERLAY && overlayController != null) {
            overlayController.notifyPersistent(sender, message, color);
        }
        if (mode == AppMode.CLI && cliApp != null) {
            cliApp.notify(sender, message, color);
        }
        System.out.println("[" + sender + "] " + message);
    }

    private void updateStatus() {
        if (mode == AppMode.OVERLAY && overlayController != null) {
            overlayController.updateStatus();
        }
        if (cliApp != null) {
            cliApp.updateStatus();
        }
    }

    private void setTaskStatus(String text, boolean running, boolean success) {
        if (mode == AppMode.OVERLAY && overlayController != null) {
            overlayController.setTaskStatus(text, running, success);
        }
    }

    private void showError(String error) {
        System.err.println("\u26a0 " + error);
        notifyUser(Messages.get("notify.error.title"), error, "#ff4444");
    }

    // ============================================================
    // Shutdown
    // ============================================================

    private void shutdown() {
        notifyUser("\ud83d\uded1 BetterVibe", "Fahre herunter...", "#ff8888");
        fileWatcher.close();
        hotkeyManager.close();
        db.close();
        appConfig.save();
    }
}
