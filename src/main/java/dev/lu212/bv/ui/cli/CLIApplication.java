package dev.lu212.bv.ui.cli;

import dev.lu212.bv.ai.ProviderManager;
import dev.lu212.bv.ai.expense.ExpenseTracker;
import dev.lu212.bv.db.GoalRepository;
import dev.lu212.bv.db.HotkeyBindingRepository;
import dev.lu212.bv.input.HotkeyAction;
import dev.lu212.bv.input.HotkeyBinding;
import dev.lu212.bv.ui.shared.KeyBindingDisplay;

import org.jline.reader.History;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class CLIApplication implements Runnable {

    private final ProviderManager providerManager;
    private final ExpenseTracker expenseTracker;
    private final GoalRepository goalRepo;
    private final HotkeyBindingRepository bindingRepo;
    private final String projectPath;
    private final Consumer<String> onMessage;
    private final Consumer<HotkeyAction> onHotkeyAction;
    private volatile Runnable onModeChanged;
    private volatile Runnable onBindingsChanged;
    private volatile BiConsumer<String, String> onProviderKeySaved;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile Map<HotkeyAction, HotkeyBinding> bindings = new LinkedHashMap<>();
    private final Deque<String> messageLines = new ArrayDeque<>();
    private final StringBuilder inputBuffer = new StringBuilder();
    private final AtomicInteger scrollOffset = new AtomicInteger(0);

    private volatile boolean waitingForProvider;
    private volatile boolean waitingForModel;
    private volatile boolean waitingForKey;
    private volatile boolean waitingForMode;
    private volatile String selectedProviderName;

    private volatile boolean streaming;
    private volatile String streamSender;
    private volatile String streamColor;
    private final StringBuilder streamContent = new StringBuilder();
    private volatile boolean streamActive;

    private volatile int termWidth = 80;
    private volatile int termHeight = 24;

    private Terminal terminal;
    private History history;
    private int historyIndex = -1;
    private String savedInput = "";

    private static final int HEADER_LINES = 3;
    private static final int FOOTER_LINES = 3;
    private static final int MAX_MESSAGES = 500;

    private static final String R = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String ITALIC = "\u001B[3m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String WHITE = "\u001B[97m";
    private static final String BLUE = "\u001B[94m";
    private static final String BG_HEADER = "\u001B[48;5;17m";
    private static final String BG_INPUT = "\u001B[48;5;233m";
    private static final String CLEAR = "\u001B[2J";
    private static final String HOME = "\u001B[H";
    private static final String REVERSE = "\u001B[7m";

    private static final List<String> COMMANDS = List.of(
        "/help", "/h",
        "/review", "/r",
        "/status", "/s",
        "/goal", "/g",
        "/config", "/c",
        "/cost",
        "/clear",
        "/exit", "/quit"
    );

    public CLIApplication(
        ProviderManager providerManager,
        ExpenseTracker expenseTracker,
        GoalRepository goalRepo,
        HotkeyBindingRepository bindingRepo,
        String projectPath,
        Consumer<String> onMessage,
        Consumer<HotkeyAction> onHotkeyAction
    ) {
        this.providerManager = providerManager;
        this.expenseTracker = expenseTracker;
        this.goalRepo = goalRepo;
        this.bindingRepo = bindingRepo;
        this.projectPath = projectPath;
        this.onMessage = onMessage;
        this.onHotkeyAction = onHotkeyAction;
    }

    public void setOnModeChanged(Runnable onModeChanged) { this.onModeChanged = onModeChanged; }
    public void setOnBindingsChanged(Runnable onBindingsChanged) { this.onBindingsChanged = onBindingsChanged; }
    public void setOnProviderKeySaved(BiConsumer<String, String> onProviderKeySaved) { this.onProviderKeySaved = onProviderKeySaved; }
    public void setBindings(Map<HotkeyAction, HotkeyBinding> bindings) { this.bindings = bindings; }

    @Override
    public void run() {
        bindings = bindingRepo.loadAll();
        Runtime.getRuntime().addShutdownHook(new Thread(this::teardown));

        try {
            terminal = TerminalBuilder.builder()
                .system(true)
                .build();
            terminal.enterRawMode();
            history = new DefaultHistory();
            refreshTermSize();
            writeRaw("\u001B[?1002h\u001B[?1006h");

            render();

            addMessageLine(BOLD + BLUE + "BetterVibe" + R, "v" + dev.lu212.bv.AppDefaults.VERSION + " – KI-Coding-Assistent");
            addMessageLine(DIM + "" + R, "  Projekt: " + projectPath);
            var pWelcome = providerManager.activeProvider();
            if (pWelcome != null) {
                var mWelcome = providerManager.activeModelId();
                addMessageLine(DIM + "" + R, "  Provider: " + pWelcome.getName() + "/" + (mWelcome != null ? mWelcome : "?"));
            } else {
                var avail = providerManager.getProviderNames();
                if (avail.isEmpty()) {
                    addMessageLine(RED + "\u26a0" + R, "Kein Provider konfiguriert – starte mit --openai-key oder --gemini-key");
                } else {
                    addMessageLine(YELLOW + "\u26a0" + R, "Kein aktiver Provider. /config provider wählen.");
                }
            }
            addMessageLine(DIM + "" + R, "  /help für Befehle, Tab für Kompletion");
            render();

            terminal.handle(Terminal.Signal.WINCH, signal -> {
                refreshTermSize();
                render();
            });

            var inputThread = new Thread(this::inputLoop, "CLI-Input");
            inputThread.setDaemon(true);
            inputThread.start();

            while (running.get()) {
                Thread.sleep(100);
            }
        } catch (Exception e) {
            fallbackMode();
        } finally {
            teardown();
        }
    }

    private void inputLoop() {
        try {
            var in = terminal.input();
            var buf = new byte[32];
            while (running.get()) {
                int n = in.read(buf);
                if (n <= 0) break;
                processInput(buf, n);
            }
        } catch (IOException ignored) {}
    }

    // ============================================================
    // Input processing
    // ============================================================

    private void processInput(byte[] buf, int len) {
        for (int i = 0; i < len; i++) {
            int b = buf[i] & 0xFF;

            if (b == 0x1B && i + 1 < len) {
                int next = buf[i + 1] & 0xFF;
                if (next == '[') {
                    i += 2;
                    if (i < len) {
                        switch (buf[i]) {
                            case 'A' -> { navigateHistory(-1); render(); }
                            case 'B' -> { navigateHistory(1); render(); }
                            case 'C','D' -> { } // left/right – ignore
                            case 'M' -> { i = parseSgrMouse(buf, i + 1, len); }
                            case 'Z' -> { doComplete(); render(); } // Shift+Tab
                            case '<' -> i = parseSgrMouseEvent(buf, i + 1, len);
                        }
                    }
                    continue;
                }
                if (next == 'O') {
                    i += 2;
                    if (i < len && buf[i] == 'H') { /* Home */ }
                    if (i < len && buf[i] == 'F') { /* End */ }
                    continue;
                }
                continue;
            }

            if (b == '\t') {
                doComplete();
                render();
                continue;
            }

            if (b == '\n' || b == '\r') {
                var text = inputBuffer.toString().strip();
                inputBuffer.setLength(0);
                historyIndex = -1;
                savedInput = "";
                if (waitingForProvider || waitingForModel || waitingForKey || waitingForMode) {
                    handleConfigInput(text);
                } else if (text.startsWith("/")) {
                    if (!text.isBlank()) handleCommand(text);
                } else if (!text.isEmpty()) {
                    addMessageLine(GREEN + "Du" + R, text);
                    onMessage.accept(text);
                }
                render();
                continue;
            }

            if (b == 127 || b == 8) {
                if (inputBuffer.length() > 0) {
                    inputBuffer.deleteCharAt(inputBuffer.length() - 1);
                    historyIndex = -1;
                    savedInput = "";
                }
                render();
                continue;
            }

            if (b >= 32 && b < 127) {
                inputBuffer.append((char) b);
                historyIndex = -1;
                savedInput = "";
                render();
            }
        }
    }

    private void navigateHistory(int direction) {
        if (history.size() == 0) return;
        if (direction < 0) {
            if (historyIndex == -1) {
                savedInput = inputBuffer.toString();
                historyIndex = history.size() - 1;
            } else if (historyIndex > 0) {
                historyIndex--;
            }
            inputBuffer.setLength(0);
            inputBuffer.append(history.get(historyIndex));
        } else {
            if (historyIndex >= history.size() - 1 || historyIndex < 0) {
                inputBuffer.setLength(0);
                inputBuffer.append(savedInput);
                historyIndex = -1;
                savedInput = "";
            } else {
                historyIndex++;
                inputBuffer.setLength(0);
                inputBuffer.append(history.get(historyIndex));
            }
        }
    }

    private void doComplete() {
        var text = inputBuffer.toString();
        if (text.startsWith("/")) {
            var match = COMMANDS.stream()
                .filter(c -> c.startsWith(text))
                .toList();
            if (match.size() == 1) {
                inputBuffer.setLength(0);
                inputBuffer.append(match.get(0));
                if (!match.get(0).endsWith(" ")) inputBuffer.append(" ");
            } else if (match.size() > 1) {
                var prefix = longestCommonPrefix(match);
                if (prefix.length() > text.length()) {
                    inputBuffer.setLength(0);
                    inputBuffer.append(prefix);
                } else {
                    addMessageLine(DIM + "?" + R, String.join("  ", match));
                }
            }
        }
    }

    private String longestCommonPrefix(List<String> strs) {
        if (strs.isEmpty()) return "";
        var first = strs.get(0);
        for (int i = 0; i < first.length(); i++) {
            char c = first.charAt(i);
            for (int j = 1; j < strs.size(); j++) {
                if (i >= strs.get(j).length() || strs.get(j).charAt(i) != c) {
                    return first.substring(0, i);
                }
            }
        }
        return first;
    }

    private int parseSgrMouse(byte[] buf, int start, int len) {
        if (start + 2 >= len) return start;
        int cb = (buf[start] & 0xFF) - 32;
        int x = (buf[start + 1] & 0xFF) - 32;
        int y = (buf[start + 2] & 0xFF) - 32;
        handleMouseEvent(cb, x, y);
        return start + 2;
    }

    private int parseSgrMouseEvent(byte[] buf, int start, int len) {
        var end = start;
        while (end < len && buf[end] != 'M' && buf[end] != 'm') end++;
        if (end >= len) return start - 2;
        if (buf[start] != '<') return end;
        var data = new String(buf, start + 1, end - start - 1, java.nio.charset.StandardCharsets.US_ASCII);
        var parts = data.split(";");
        if (parts.length == 3) {
            try {
                int btn = Integer.parseInt(parts[0]);
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                handleMouseEvent(btn, x, y);
            } catch (NumberFormatException ignored) {}
        }
        return end;
    }

    private void handleMouseEvent(int button, int x, int y) {
        int msgLines = termHeight - HEADER_LINES - FOOTER_LINES;
        if (msgLines <= 0) return;
        int maxScroll = Math.max(0, messageLines.size() - msgLines);
        if (button == 64) {
            scrollOffset.updateAndGet(v -> Math.min(v + 3, maxScroll));
            render();
        } else if (button == 65) {
            scrollOffset.updateAndGet(v -> Math.max(v - 3, 0));
            render();
        }
    }

    // ============================================================
    // Config wizard
    // ============================================================

    private void handleConfigInput(String text) {
        if (waitingForProvider) {
            waitingForProvider = false;
            if (providerManager.setActiveProvider(text)) {
                var provider = providerManager.getProvider(text);
                if (provider != null && !provider.getModels().isEmpty()) {
                    addMessageLine(CYAN + "i" + R, "Provider \"" + text + "\" aktiv.");
                    for (var m : provider.getModels()) {
                        addMessageLine(DIM + "" + R, "  " + m.id());
                    }
                    addMessageLine(CYAN + "i" + R, "Modell eingeben (Enter = erstes):");
                    selectedProviderName = text;
                    waitingForModel = true;
                } else {
                    goalRepo.setProvider(projectPath, text, null);
                    addMessageLine(GREEN + "\u2713" + R, "Provider \"" + text + "\" aktiv.");
                    updateStatus();
                }
            } else {
                addMessageLine(RED + "\u2717" + R, "Unbekannter Provider \"" + text + "\". Verfügbar:");
                for (var n : providerManager.getProviderNames()) {
                    addMessageLine(DIM + "" + R, "  " + n);
                }
            }
        } else if (waitingForModel) {
            waitingForModel = false;
            var modelId = text.isBlank() ? null : text;
            if (modelId != null && !providerManager.setActiveModel(modelId)) {
                addMessageLine(YELLOW + "!" + R, "Ungültiges Modell. Verwende erstes.");
                var provider = providerManager.getProvider(selectedProviderName);
                if (provider != null && !provider.getModels().isEmpty()) {
                    modelId = provider.getModels().get(0).id();
                    providerManager.setActiveModel(modelId);
                }
            }
            if (modelId == null) {
                var provider = providerManager.getProvider(selectedProviderName);
                if (provider != null && !provider.getModels().isEmpty()) {
                    modelId = provider.getModels().get(0).id();
                    providerManager.setActiveModel(modelId);
                }
            }
            if (selectedProviderName != null && modelId != null) {
                goalRepo.setProvider(projectPath, selectedProviderName, modelId);
                addMessageLine(GREEN + "\u2713" + R, "Gespeichert: " + selectedProviderName + "/" + modelId);
                addMessageLine(CYAN + "i" + R, "API-Key eingeben (oder Enter = leer):");
                waitingForKey = true;
            } else {
                selectedProviderName = null;
            }
        } else if (waitingForKey) {
            waitingForKey = false;
            if (!text.isBlank() && selectedProviderName != null) {
                providerManager.saveApiKey(selectedProviderName, text);
                if (onProviderKeySaved != null) onProviderKeySaved.accept(selectedProviderName, text);
                history.add(text);
                addMessageLine(GREEN + "\u2713" + R, "API-Key gespeichert.");
            } else {
                addMessageLine(DIM + "i" + R, "Kein API-Key gesetzt.");
            }
            selectedProviderName = null;
            updateStatus();
        } else if (waitingForMode) {
            waitingForMode = false;
            if ("auto".equals(text) || "manual".equals(text)) {
                goalRepo.setMode(projectPath, text);
                addMessageLine(GREEN + "\u2713" + R, "Modus auf \"" + text + "\" gesetzt.");
                if (onModeChanged != null) onModeChanged.run();
            } else {
                addMessageLine(RED + "\u2717" + R, "Ungültig. Nutze \"auto\" oder \"manual\".");
            }
            updateStatus();
        }
        scrollOffset.set(0);
    }

    // ============================================================
    // Commands
    // ============================================================

    private void handleCommand(String cmd) {
        scrollOffset.set(0);
        var parts = cmd.substring(1).strip().split("\\s+", 2);
        var command = parts[0].toLowerCase();
        var arg = parts.length > 1 ? parts[1] : "";

        history.add("/" + command + (arg.isEmpty() ? "" : " " + arg));

        switch (command) {
            case "help", "h" -> showHelp();
            case "exit", "quit" -> {
                addMessageLine(MAGENTA + "" + R, "Tschüss!");
                render();
                running.set(false);
            }
            case "clear" -> {
                messageLines.clear();
                render();
            }
            case "review", "r" -> {
                addMessageLine(CYAN + "i" + R, "Review gestartet...");
                render();
                onHotkeyAction.accept(HotkeyAction.REVIEW_CODE);
            }
            case "status", "s" -> {
                addMessageLine(CYAN + "i" + R, "Status wird abgerufen...");
                render();
                onMessage.accept("/status");
            }
            case "goal", "g" -> {
                if (arg.isBlank()) {
                    var existing = goalRepo.getGoal(projectPath).orElse("");
                    addMessageLine(YELLOW + "!" + R, existing.isEmpty() ? "Kein Ziel gesetzt." : "Ziel: " + existing);
                } else {
                    goalRepo.setGoal(projectPath, arg);
                    addMessageLine(GREEN + "\u2713" + R, "Ziel gespeichert: " + arg);
                    updateStatus();
                }
                render();
            }
            case "cost" -> showCost();
            case "config", "c" -> showConfig(arg);
            default -> {
                addMessageLine(RED + "\u2717" + R, "Unbekannter Befehl: /" + command + ". /help für Hilfe.");
                render();
            }
        }
    }

    private void showHelp() {
        addMessageLine(BOLD + "?" + R, "Befehle");
        addMessageLine(DIM + "" + R, "  /review, /r        – Code-Änderungen reviewen");
        addMessageLine(DIM + "" + R, "  /status, /s        – Status anzeigen");
        addMessageLine(DIM + "" + R, "  /goal, /g [text]   – Ziel anzeigen/setzen");
        addMessageLine(DIM + "" + R, "  /cost              – Kostenübersicht");
        addMessageLine(DIM + "" + R, "  /config, /c        – Konfiguration anzeigen");
        addMessageLine(DIM + "" + R, "  /config provider   – Provider auswählen");
        addMessageLine(DIM + "" + R, "  /config model      – Modell auswählen");
        addMessageLine(DIM + "" + R, "  /config key        – API-Key eingeben");
        addMessageLine(DIM + "" + R, "  /config mode       – Modus wechseln (auto/manual)");
        addMessageLine(DIM + "" + R, "  /config bind       – Tastenkürzel anzeigen/ändern");
        addMessageLine(DIM + "" + R, "  /clear             – Nachrichten löschen");
        addMessageLine(DIM + "" + R, "  /exit              – Beenden");
        addMessageLine(DIM + "" + R, "  Tab-Kompletion, Pfeil hoch/runter für Verlauf");
        addMessageLine(DIM + "" + R, "  Shortcuts: " + KeyBindingDisplay.allBindings(bindings));
        render();
    }

    private void showCost() {
        var summary = expenseTracker.getSummary();
        addMessageLine(BOLD + "$" + R, "Kostenübersicht");
        addMessageLine(DIM + "" + R, "  Gesamtkosten:  " + BOLD + expenseTracker.getTotalCostFormatted() + R);
        addMessageLine(DIM + "" + R, "  Heute:         " + BOLD + expenseTracker.getCostTodayFormatted() + R);
        addMessageLine(DIM + "" + R, "  API-Calls:     " + summary.totalCalls());
        addMessageLine(DIM + "" + R, "  Input-Tokens:  " + formatNumber(summary.promptTokens()));
        addMessageLine(DIM + "" + R, "  Output-Tokens: " + formatNumber(summary.completionTokens()));
        render();
    }

    private String formatNumber(int n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return String.format("%.1fK", n / 1000.0);
        return String.format("%.2fM", n / 1_000_000.0);
    }

    private void showConfig(String arg) {
        var parts = arg.split("\\s+", 2);
        var sub = parts[0];
        var subArg = parts.length > 1 ? parts[1] : "";
        switch (sub) {
            case "provider", "p" -> {
                addMessageLine(CYAN + "i" + R, "Verfügbare Provider:");
                for (var n : providerManager.getProviderNames()) {
                    addMessageLine(DIM + "" + R, "  " + n);
                }
                addMessageLine(CYAN + "i" + R, "Provider eingeben:");
                waitingForProvider = true;
                render();
            }
            case "model" -> {
                var p = providerManager.activeProvider();
                if (p == null) {
                    addMessageLine(RED + "\u2717" + R, "Kein aktiver Provider. Zuerst /config provider.");
                } else {
                    addMessageLine(CYAN + "i" + R, "Modelle für " + p.getName() + ":");
                    for (var m : p.getModels()) {
                        addMessageLine(DIM + "" + R, "  " + m.id());
                    }
                    addMessageLine(CYAN + "i" + R, "Modell eingeben (Enter = erstes):");
                    selectedProviderName = p.getName();
                    waitingForModel = true;
                }
                render();
            }
            case "key" -> {
                var p = providerManager.activeProvider();
                if (p == null) {
                    addMessageLine(RED + "\u2717" + R, "Kein aktiver Provider.");
                } else {
                    selectedProviderName = p.getName();
                    addMessageLine(CYAN + "i" + R, "API-Key für " + p.getName() + " eingeben:");
                    waitingForKey = true;
                }
                render();
            }
            case "mode" -> {
                var current = goalRepo.getMode(projectPath).orElse("manual");
                addMessageLine(YELLOW + "!" + R, "Aktueller Modus: " + current);
                addMessageLine(CYAN + "i" + R, "\"auto\" oder \"manual\" eingeben:");
                waitingForMode = true;
                render();
            }
            case "bind" -> {
                if (subArg.isEmpty()) {
                    showBindConfig();
                } else {
                    handleBindChange(subArg);
                }
                render();
            }
            default -> {
                var p = providerManager.activeProvider();
                var m = providerManager.activeModelId();
                var mode = goalRepo.getMode(projectPath).orElse("manual");
                var goal = goalRepo.getGoal(projectPath).orElse("(kein)");
                var summary = expenseTracker.getSummary();
                addMessageLine(BOLD + "\u2699" + R, "Konfiguration");
                addMessageLine(DIM + "" + R, "  Provider: " + (p != null ? p.getName() : "–"));
                addMessageLine(DIM + "" + R, "  Modell:   " + (m != null ? m : "–"));
                addMessageLine(DIM + "" + R, "  Modus:    " + mode);
                addMessageLine(DIM + "" + R, "  Kosten:   " + expenseTracker.getTotalCostFormatted());
                addMessageLine(DIM + "" + R, "  Calls:    " + summary.totalCalls());
                addMessageLine(DIM + "" + R, "  Tokens:   " + formatNumber(summary.promptTokens()) + " / " + formatNumber(summary.completionTokens()));
                addMessageLine(DIM + "" + R, "  Ziel:     " + goal);
                addMessageLine(DIM + "" + R, "  Projekt:  " + projectPath);
                addMessageLine(DIM + "" + R, "  /config provider – Provider wechseln");
                addMessageLine(DIM + "" + R, "  /config key      – API-Key setzen");
                addMessageLine(DIM + "" + R, "  /config mode     – Modus wechseln");
                addMessageLine(DIM + "" + R, "  /config bind     – Tastenkürzel ändern");
                render();
            }
        }
    }

    private void showBindConfig() {
        addMessageLine(BOLD + "\u2328" + R, "Aktuelle Tastenkürzel:");
        for (var entry : bindings.entrySet()) {
            var action = entry.getKey();
            var binding = entry.getValue();
            addMessageLine(DIM + "" + R, "  " + KeyBindingDisplay.format(action, binding));
        }
        addMessageLine(DIM + "" + R, "");
        addMessageLine(DIM + "" + R, "  Ändern: /config bind <aktion> <taste>");
        addMessageLine(DIM + "" + R, "  Aktionen: review, toggle");
        addMessageLine(DIM + "" + R, "  Tasten:   A-Z, 0-9, F1-F12, SPACE, ENTER, TAB, ESCAPE");
        addMessageLine(DIM + "" + R, "  Beispiel: /config bind review F5");
    }

    // ============================================================
    // Rendering
    // ============================================================

    private synchronized void render() {
        var w = termWidth;
        var h = termHeight;
        var msgLines = Math.max(1, h - HEADER_LINES - FOOTER_LINES);

        var sb = new StringBuilder();
        sb.append(HOME);

        // --- Header (3 lines) ---
        var p = providerManager.activeProvider();
        var m = providerManager.activeModelId();
        var cost = expenseTracker.getTotalCostFormatted();
        var mode = goalRepo.getMode(projectPath).orElse("manual");
        var summary = expenseTracker.getSummary();

        // Line 1: Title + Costs
        sb.append(BG_HEADER).append(WHITE).append(BOLD);
        sb.append(padCenter(" BetterVibe ", w));
        sb.append(R).append("\n");

        // Line 2: Provider/Model | Mode | Cost
        sb.append(BG_HEADER).append(WHITE);
        sb.append(" ").append(p != null ? p.getName() : "–").append("/").append(m != null ? m : "–");
        sb.append(DIM).append(" \u2502 ").append(R).append(WHITE).append("MD:").append(mode);
        sb.append(DIM).append(" \u2502 ").append(R).append(YELLOW).append(cost);
        sb.append(DIM).append(padRight("", w - (p != null ? p.getName().length() + 2 + (m != null ? m.length() : 1) : 4) - mode.length() - cost.length() - 10)).append(R);
        sb.append("\n");

        // Line 3: Cost details
        sb.append(BG_HEADER).append(DIM);
        sb.append(" \u26A1 ").append(summary.totalCalls()).append(" Calls");
        sb.append("  \u2191 ").append(formatNumber(summary.promptTokens())).append(" in");
        sb.append("  \u2193 ").append(formatNumber(summary.completionTokens())).append(" out");
        sb.append("  ").append(BOLD).append("$").append(String.format("%.4f", summary.totalCost())).append(R);
        sb.append(BG_HEADER).append(DIM).append(padRight("", w - 50)).append(R);
        sb.append("\n");

        // --- Messages ---
        var allLines = java.util.List.copyOf(messageLines);
        int maxScroll = Math.max(0, allLines.size() - msgLines);
        if (scrollOffset.get() > maxScroll) scrollOffset.set(maxScroll);
        int start = Math.max(0, allLines.size() - msgLines - scrollOffset.get());
        int end = Math.min(allLines.size(), start + msgLines);

        for (int i = start; i < end; i++) {
            sb.append(allLines.get(i));
            sb.append("\n");
        }
        int empty = msgLines - (end - start);
        for (int i = 0; i < empty; i++) {
            sb.append("\n");
        }

        // --- Footer ---
        var scrollInfo = scrollOffset.get() > 0
            ? " (Mausrad, " + scrollOffset.get() + ")" : "";
        sb.append(DIM);
        sb.append(padRight(scrollInfo, w)).append(R).append("\n");

        // Input line with visual indicator
        sb.append(BG_INPUT).append(GREEN).append(BOLD).append(" \u276F ");
        sb.append(R).append(BG_INPUT).append(WHITE);
        var displayBuf = inputBuffer.toString();
        sb.append(displayBuf);
        sb.append(DIM).append("\u258C"); // cursor block
        var inputLen = 3 + displayBuf.length() + 1;
        sb.append(padRight("", Math.max(0, w - inputLen))).append(R);
        sb.append("\n");

        // Hints with cost
        var hintCost = "Kosten: " + expenseTracker.getTotalCostFormatted();
        var hints = "/review /status /goal /cost /config /help";
        var combined = hints + DIM + "  " + hintCost;
        sb.append(DIM);
        sb.append(padRight(combined, w));
        sb.append(R);

        writeRaw(sb.toString());
    }

    private void writeRaw(String s) {
        try {
            System.out.write(s.getBytes());
            System.out.flush();
        } catch (IOException ignored) {}
    }

    // ============================================================
    // Fallback mode
    // ============================================================

    private void fallbackMode() {
        addMessageLine(DIM + "" + R, "Einfacher Modus – /exit zum Beenden, /help für Befehle");
        render();
        try {
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
            while (running.get()) {
                System.out.print("\n> ");
                System.out.flush();
                var line = reader.readLine();
                if (line == null) break;
                line = line.strip();
                if (line.startsWith("/")) {
                    handleCommand(line);
                } else if (!line.isEmpty()) {
                    addMessageLine(GREEN + "Du" + R, line);
                    onMessage.accept(line);
                    render();
                }
            }
        } catch (IOException ignored) {}
    }

    // ============================================================
    // Terminal management
    // ============================================================

    private void teardown() {
        running.set(false);
        try {
            writeRaw("\u001B[?1006l\u001B[?1002l\u001B[?1000l");
            writeRaw("\u001B[?25h\u001B[0m" + CLEAR + HOME);
            if (terminal != null) terminal.close();
        } catch (Exception ignored) {}
    }

    private void refreshTermSize() {
        if (terminal != null) {
            termWidth = terminal.getWidth();
            termHeight = terminal.getHeight();
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void addMessageLine(String prefix, String text) {
        int maxW = Math.max(40, termWidth - 4);
        if (text.length() > maxW) text = text.substring(0, maxW - 3) + "...";
        var line = WHITE + " " + prefix + R + " " + text;
        messageLines.addLast(line);
        if (messageLines.size() > MAX_MESSAGES) messageLines.removeFirst();
    }

    private String padCenter(String s, int w) {
        if (s.length() >= w) return s;
        int left = (w - s.length()) / 2;
        int right = w - s.length() - left;
        return " ".repeat(left) + s + " ".repeat(right);
    }

    private String padRight(String s, int w) {
        if (s.length() >= w) return s;
        return s + " ".repeat(w - s.length());
    }

    // ============================================================
    // Public API (called from BetterVibeApp)
    // ============================================================

    public void notify(String sender, String text, String color) {
        addMessageLine(color + sender + R, text);
        scrollOffset.set(0);
        render();
    }

    public void showInput() {}

    public void updateStatus() {
        render();
    }

    public void startStream(String sender, String color) {
        streamSender = sender;
        streamColor = color;
        streamContent.setLength(0);
        streamActive = true;
        var line = streamColor + "[" + streamSender + "]" + R + " ";
        messageLines.addLast(line);
        scrollOffset.set(0);
    }

    public void appendStream(String chunk) {
        if (!streamActive) return;
        streamContent.append(chunk);
        var line = streamColor + "[" + streamSender + "]" + R + " " + streamContent.toString();
        if (!messageLines.isEmpty()) messageLines.removeLast();
        messageLines.addLast(line);
        render();
    }

    public String finishStream() {
        streamActive = false;
        scrollOffset.set(0);
        render();
        return streamContent.toString();
    }

    public void notifyStreamError(String message) {
        if (streamActive) {
            streamActive = false;
            if (!messageLines.isEmpty()) messageLines.removeLast();
        }
        addMessageLine(RED + "\u2717" + R, "Fehler: " + message);
        render();
    }

    private void handleBindChange(String arg) {
        var parts = arg.split("\\s+", 2);
        if (parts.length < 2) {
            addMessageLine(RED + "\u2717" + R, "Syntax: /config bind <aktion> <taste>");
            render();
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
            addMessageLine(RED + "\u2717" + R, "Unbekannte Aktion: " + actionName + ". Verfügbar: review, toggle");
            render();
            return;
        }

        var keyCode = HotkeyAction.keyCodeForName(keyName);
        if (keyCode < 0) {
            addMessageLine(RED + "\u2717" + R, "Unbekannte Taste: " + keyName);
            render();
            return;
        }

        var binding = new HotkeyBinding(keyCode, keyName);
        bindingRepo.save(action, binding);
        bindings.put(action, binding);
        if (onBindingsChanged != null) onBindingsChanged.run();
        addMessageLine(GREEN + "\u2713" + R, "Tastenkürzel: " + KeyBindingDisplay.format(action, binding));
        render();
    }
}
