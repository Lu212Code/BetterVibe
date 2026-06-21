package dev.lu212.bv.ui.overlay;

import dev.lu212.bv.BetterVibeApp;
import dev.lu212.bv.ai.ProviderManager;
import dev.lu212.bv.ai.expense.ExpenseTracker;
import dev.lu212.bv.config.AppConfig;
import dev.lu212.bv.db.GoalRepository;
import dev.lu212.bv.db.HotkeyBindingRepository;
import dev.lu212.bv.db.MessageRepository;
import dev.lu212.bv.db.ProviderConfigRepository;
import dev.lu212.bv.input.HotkeyAction;
import dev.lu212.bv.input.HotkeyBinding;
import dev.lu212.bv.ui.shared.KeyBindingDisplay;
import dev.lu212.bv.ui.shared.MarkdownRenderer;
import dev.lu212.bv.watch.FileWatcher;
import dev.lu212.bv.i18n.Messages;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class OverlayApplication extends Application {

    private static final String E_BG = "#1e1e1e";
    private static final String E_PANEL = "#252526";
    private static final String E_BORDER = "#3c3c3c";
    private static final String E_TEXT = "#cccccc";
    private static final String E_TEXT_DIM = "#8a8a8a";
    private static final String E_ACCENT = "#4a7fa5";

    private static ProviderManager staticProviderManager;
    private static ExpenseTracker staticExpenseTracker;
    private static GoalRepository staticGoalRepo;
    private static ProviderConfigRepository staticProviderConfigRepo;
    private static HotkeyBindingRepository staticBindingRepo;
    private static FileWatcher staticFileWatcher;
    private static String staticProjectPath;
    private static Consumer<String> staticOnMessage;
    private static Consumer<HotkeyAction> staticOnHotkeyAction;
    private static Runnable staticOnModeChanged;
    private static BiConsumer<String, String> staticOnProviderKeySaved;
    private static AppConfig staticAppConfig;
    private static MessageRepository staticMessageRepo;

    private static OverlayController controller;
    private static String scrollbarCssUrl;

    private static void ensureScrollbarCss() {
        if (scrollbarCssUrl != null) return;
        try {
            var css = """
                .scroll-bar { -fx-background-color: #1a1a1a; -fx-background-insets: 0; }
                .scroll-bar .thumb { -fx-background-color: #3c3c3c; -fx-background-insets: 2; -fx-background-radius: 4; }
                .scroll-bar .thumb:hover { -fx-background-color: #4a4a4a; }
                .scroll-bar .track { -fx-background-color: #1a1a1a; }
                .scroll-bar .increment-button, .scroll-bar .decrement-button {
                    -fx-background-color: #1a1a1a; -fx-background-insets: 0; -fx-padding: 0;
                }
                .scroll-bar .increment-arrow, .scroll-bar .decrement-arrow { -fx-background-color: #555; }
                .scroll-pane > .corner { -fx-background-color: #1a1a1a; }
                """;
            var file = File.createTempFile("bettervibe-scrollbar", ".css");
            file.deleteOnExit();
            Files.writeString(file.toPath(), css);
            scrollbarCssUrl = file.toURI().toURL().toString();
        } catch (IOException e) {}
    }

    private ProviderManager providerManager;
    private ExpenseTracker expenseTracker;
    private GoalRepository goalRepo;
    private ProviderConfigRepository providerConfigRepo;
    private HotkeyBindingRepository bindingRepo;
    private FileWatcher fileWatcher;
    private AppConfig appConfig;
    private MessageRepository messageRepo;
    private String projectPath;
    private Consumer<String> onMessage;
    private Consumer<HotkeyAction> onHotkeyAction;
    private Runnable onModeChanged;
    private BiConsumer<String, String> onProviderKeySaved;
    private OverlayConfigDialog configDialog;
    private Map<HotkeyAction, HotkeyBinding> bindings = new LinkedHashMap<>();
    private Map<HotkeyAction, Label> bindingLabels = new LinkedHashMap<>();

    private Stage stage;
    private VBox toastArea;
    private VBox messageArea;
    private ScrollPane messageScroll;
    private Label statusLabel;
    private Label providerLabel;
    private Label taskStatusLabel;
    private TextField inputField;
    private int overlayWidth;
    private int overlayHeight;
    private int loadedMessageCount;
    private Label loadOlderLabel;
    private boolean visible = true;
    private Timeline spinnerAnimation;

    public static void initStatic(
        ProviderManager pm, ExpenseTracker et, GoalRepository gr,
        ProviderConfigRepository pcr, HotkeyBindingRepository br, MessageRepository mr, FileWatcher fw,
        AppConfig ac, String pp, Consumer<String> om, Consumer<HotkeyAction> oh,
        Runnable omc, BiConsumer<String, String> oks
    ) {
        staticProviderManager = pm;
        staticExpenseTracker = et;
        staticGoalRepo = gr;
        staticProviderConfigRepo = pcr;
        staticBindingRepo = br;
        staticMessageRepo = mr;
        staticFileWatcher = fw;
        staticAppConfig = ac;
        staticProjectPath = pp;
        staticOnMessage = om;
        staticOnHotkeyAction = oh;
        staticOnModeChanged = omc;
        staticOnProviderKeySaved = oks;
    }

    public static OverlayController getController() { return controller; }

    public OverlayApplication() {
        this.providerManager = staticProviderManager;
        this.expenseTracker = staticExpenseTracker;
        this.goalRepo = staticGoalRepo;
        this.providerConfigRepo = staticProviderConfigRepo;
        this.bindingRepo = staticBindingRepo;
        this.fileWatcher = staticFileWatcher;
        this.appConfig = staticAppConfig;
        this.messageRepo = staticMessageRepo;
        this.projectPath = staticProjectPath;
        this.onMessage = staticOnMessage;
        this.onHotkeyAction = staticOnHotkeyAction;
        this.onModeChanged = staticOnModeChanged;
        this.onProviderKeySaved = staticOnProviderKeySaved;
        this.configDialog = new OverlayConfigDialog(
            providerManager, goalRepo, providerConfigRepo, fileWatcher, appConfig, projectPath
        );
        this.configDialog.setOnModeChanged(this.onModeChanged);
        this.configDialog.setOnKeySaved(this.onProviderKeySaved);
        this.configDialog.setOnConfigApplied(() -> {
            overlayWidth = appConfig.getInt("overlay_width", 680);
            overlayHeight = appConfig.getInt("overlay_height", 360);
            Platform.runLater(() -> {
                positionOverlay();
                updateStatus();
                checkProviderConnection();
            });
        });
        controller = new OverlayController(this);
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        initOverlay();
    }

    private void initOverlay() {
        bindings = bindingRepo.loadAll();
        overlayWidth = appConfig.getInt("overlay_width", 680);
        overlayHeight = appConfig.getInt("overlay_height", 360);

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setTitle("BetterVibe");

        var root = new StackPane();
        root.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;",
            E_BG, E_BORDER
        ));

        var shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.5));
        shadow.setRadius(12);
        root.setEffect(shadow);

        var mainColumn = new VBox();
        mainColumn.setMaxHeight(Double.MAX_VALUE);
        mainColumn.setMaxWidth(Double.MAX_VALUE);
        mainColumn.getChildren().add(createToolbar());

        messageArea = new VBox(6);
        messageArea.setPadding(new Insets(4, 10, 4, 10));
        messageArea.setStyle(String.format("-fx-background-color: %s;", "#1a1a1a"));

        loadOlderLabel = new Label(Messages.get("overlay.loadolder"));
        loadOlderLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: 10; -fx-cursor: hand; -fx-padding: 4 0; -fx-alignment: center;",
            E_ACCENT
        ));
        loadOlderLabel.setMaxWidth(Double.MAX_VALUE);
        loadOlderLabel.setAlignment(Pos.CENTER);
        loadOlderLabel.setVisible(false);
        loadOlderLabel.setOnMouseClicked(e -> loadOlderMessages());
        messageArea.getChildren().add(loadOlderLabel);

        messageScroll = new ScrollPane(messageArea);
        messageScroll.setFitToWidth(true);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        messageScroll.setMaxHeight(overlayHeight - 80);
        messageScroll.setStyle(String.format(
            "-fx-background-color: %s; -fx-background: %s; -fx-border-color: %s; -fx-border-width: 1 0 0 0;",
            "#1a1a1a", "#1a1a1a", E_BORDER
        ));

        messageScroll.vvalueProperty().addListener((obs, old, val) -> {
            if (val.doubleValue() < 0.05 && messageRepo.countMessages(projectPath) > loadedMessageCount) {
                loadOlderLabel.setVisible(true);
            } else {
                loadOlderLabel.setVisible(false);
            }
        });

        inputField = createInputField();
        mainColumn.getChildren().add(messageScroll);
        mainColumn.getChildren().add(inputField);
        mainColumn.getChildren().add(createBottomBar());

        VBox.setVgrow(messageScroll, Priority.ALWAYS);

        toastArea = new VBox(4);
        toastArea.setMouseTransparent(true);

        root.getChildren().add(mainColumn);
        root.getChildren().add(toastArea);

        StackPane.setAlignment(toastArea, Pos.BOTTOM_LEFT);
        StackPane.setMargin(toastArea, new Insets(0, 10, 50, 10));

        var scene = new Scene(root, Color.TRANSPARENT);
        ensureScrollbarCss();
        if (scrollbarCssUrl != null) scene.getStylesheets().add(scrollbarCssUrl);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                System.exit(0);
                return;
            }
            for (var entry : bindings.entrySet()) {
                var b = entry.getValue();
                if (b.keyName() == null) continue;
                var matchesMods = b.ctrlRequired() == e.isControlDown()
                    && b.altRequired() == e.isAltDown()
                    && b.shiftRequired() == e.isShiftDown();
                if (!matchesMods) continue;
                var code = keyCode(b.keyName());
                if (code != null && e.getCode() == code) {
                    e.consume();
                    onHotkeyAction.accept(entry.getKey());
                    return;
                }
            }
        });

        stage.setScene(scene);
        stage.setOpacity(0.95);
        positionOverlay();
        stage.show();

        root.setOpacity(0);
        var fadeIn = new FadeTransition(Duration.millis(150), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        configDialog.setOwner(stage);
        var app = BetterVibeApp.getInstance();
        if (app != null) app.setOverlayController(controller);
        Platform.runLater(() -> {
            inputField.requestFocus();
            checkProviderConnection();
        });
    }

    private void checkProviderConnection() {
        var provider = providerManager.activeProvider();
        if (provider == null) {
            setTaskStatus(Messages.get("overlay.status.noprovider"), false, false);
            return;
        }
        if (!provider.isAvailable()) {
            setTaskStatus(Messages.get("overlay.status.unavailable", provider.getName()), false, false);
            return;
        }
        setTaskStatus(Messages.get("overlay.status.connected", provider.getName()), false, true);
    }

    private KeyCode keyCode(String keyName) {
        if (keyName == null) return null;
        if (keyName.length() == 1) {
            var c = keyName.toUpperCase().charAt(0);
            if (c >= 'A' && c <= 'Z') return KeyCode.valueOf(String.valueOf(c));
        }
        try {
            return KeyCode.valueOf(keyName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private HBox createToolbar() {
        var bar = new HBox(8);
        bar.setPadding(new Insets(8, 14, 6, 14));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 6 6 0 0;", E_PANEL));

        var icon = new Label("BV");
        icon.setStyle(String.format(
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11; -fx-padding: 2 6; -fx-background-color: %s; -fx-background-radius: 3; -fx-cursor: hand;",
            E_ACCENT
        ));
        icon.setTooltip(new Tooltip(Messages.get("overlay.icon.tooltip")));
        icon.setOnMouseClicked(e -> showConfig());

        var title = new Label("BetterVibe");
        title.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', 'System';",
            E_TEXT
        ));

        providerLabel = new Label(getProviderDisplay());
        providerLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';", E_ACCENT
        ));

        statusLabel = new Label(expenseTracker.getTotalCostFormatted());
        statusLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';", "#6a9955"
        ));

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var bindingBar = new HBox(4);
        bindingBar.setAlignment(Pos.CENTER_RIGHT);
        for (var entry : bindings.entrySet()) {
            var action = entry.getKey();
            var binding = entry.getValue();
            if (action == HotkeyAction.TOGGLE_OVERLAY) continue;
            var label = new Label(KeyBindingDisplay.format(action, binding));
            label.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 10; -fx-font-family: 'Segoe UI', 'System';", E_TEXT_DIM
            ));
            bindingLabels.put(action, label);
            bindingBar.getChildren().add(label);
        }

        bar.getChildren().addAll(icon, title, providerLabel, spacer, statusLabel, bindingBar);
        return bar;
    }

    public void flashBinding(HotkeyAction action, boolean success) {
        var label = bindingLabels.get(action);
        if (label == null) return;
        Platform.runLater(() -> {
            var flashColor = success ? "#66ff88" : "#ff6644";
            label.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 10; -fx-font-family: 'Segoe UI', 'System'; -fx-font-weight: bold;", flashColor
            ));
            var revert = new Timeline(new KeyFrame(Duration.millis(1200), e ->
                label.setStyle(String.format(
                    "-fx-text-fill: %s; -fx-font-size: 10; -fx-font-family: 'Segoe UI', 'System';", E_TEXT_DIM
                ))
            ));
            revert.play();
        });
    }

    public void setTaskStatus(String text, boolean running, boolean success) {
        Platform.runLater(() -> {
            if (spinnerAnimation != null) {
                spinnerAnimation.stop();
                spinnerAnimation = null;
            }
            if (running) {
                var chars = new char[]{'\u25D0', '\u25D3', '\u25D1', '\u25D2'};
                var idx = new int[]{0};
                spinnerAnimation = new Timeline(new KeyFrame(Duration.millis(200), e -> {
                    taskStatusLabel.setText(chars[idx[0]] + " " + text);
                    idx[0] = (idx[0] + 1) % chars.length;
                }));
                spinnerAnimation.setCycleCount(Animation.INDEFINITE);
                spinnerAnimation.play();
                taskStatusLabel.setStyle(String.format(
                    "-fx-text-fill: %s; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';", E_ACCENT
                ));
            } else {
                var icon = success ? "\u2713 " : "\u2717 ";
                var color = success ? "#66ff88" : "#ff6644";
                taskStatusLabel.setText(icon + text);
                taskStatusLabel.setStyle(String.format(
                    "-fx-text-fill: %s; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';", color
                ));
            }
        });
    }

    private TextField createInputField() {
        var input = new TextField();
        input.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-prompt-text-fill: %s; " +
            "-fx-border-color: %s; -fx-border-width: 1 0 0 0; -fx-padding: 8 14; -fx-font-size: 12; -fx-font-family: 'Segoe UI', 'System';" +
            "-fx-background-radius: 0; -fx-border-radius: 0;",
            "#1a1a1a", E_TEXT, E_TEXT_DIM, E_BORDER
        ));
        input.setPromptText(Messages.get("overlay.input.prompt"));
        input.setOnAction(e -> {
            var text = input.getText().trim();
            if (!text.isEmpty()) {
                onMessage.accept(text);
                input.clear();
            }
        });

        return input;
    }

    private HBox createBottomBar() {
        taskStatusLabel = new Label("");
        taskStatusLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';", E_TEXT_DIM
        ));

        var bar = new HBox();
        bar.setPadding(new Insets(3, 14, 4, 14));
        bar.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 0 0 6 6; -fx-border-color: %s; -fx-border-width: 1 0 0 0;",
            E_PANEL, E_BORDER
        ));
        bar.getChildren().add(taskStatusLabel);
        return bar;
    }

    public void showConfig() {
        configDialog.show();
    }

    public void addNotification(String sender, String message, String color) {
        Platform.runLater(() -> {
            var toast = new HBox(8);
            toast.setPadding(new Insets(6, 12, 6, 12));
            toast.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4;",
                "#252526", E_BORDER
            ));
            toast.setOpacity(0);

            var header = new Label(sender);
            header.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';", color
            ));

            var msg = new Label(message);
            msg.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';", E_TEXT
            ));
            msg.setWrapText(true);
            msg.setMaxWidth(460);

            toast.getChildren().addAll(header, msg);
            toastArea.getChildren().add(toast);

            var fadeIn = new FadeTransition(Duration.millis(200), toast);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();

            var fadeOut = new FadeTransition(Duration.seconds(1), toast);
            fadeOut.setDelay(Duration.seconds(1.5));
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> Platform.runLater(() -> toastArea.getChildren().remove(toast)));
            fadeOut.play();

            while (toastArea.getChildren().size() > 5) {
                toastArea.getChildren().remove(0);
            }
        });
    }

    public void toggleVisibility() {
        visible = !visible;
        Platform.runLater(() -> {
            if (visible) {
                stage.show();
                positionOverlay();
                inputField.requestFocus();
            } else {
                stage.hide();
            }
        });
    }

    private javafx.scene.web.WebView createWebView(String message) {
        var wv = new javafx.scene.web.WebView();
        wv.setMaxWidth(640);
        wv.setPrefHeight(1);
        wv.setMinHeight(1);
        wv.setContextMenuEnabled(true);
        wv.setFocusTraversable(false);
        wv.getEngine().loadContent(MarkdownRenderer.htmlTemplate(MarkdownRenderer.toHtml(message)));
        Runnable updateHeight = () -> {
            var h = wv.getEngine().executeScript("document.body.scrollHeight");
            if (h instanceof Number n && n.doubleValue() > 0) {
                wv.setPrefHeight(n.doubleValue() + 10);
            }
        };
        wv.getEngine().getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == javafx.concurrent.Worker.State.SUCCEEDED) {
                Platform.runLater(() -> Platform.runLater(updateHeight));
            }
        });
        wv.widthProperty().addListener((obs, old, w) -> {
            if (w.doubleValue() > 0 && Math.abs(w.doubleValue() - old.doubleValue()) > 0.5) {
                Platform.runLater(updateHeight);
            }
        });
        Platform.runLater(() -> Platform.runLater(updateHeight));
        wv.addEventFilter(ScrollEvent.SCROLL, e -> {
            var delta = -e.getDeltaY();
            var cur = messageScroll.getVvalue();
            var max = Math.max(1, messageScroll.getVmax());
            messageScroll.setVvalue(Math.max(0, Math.min(cur + delta / (messageScroll.getHeight() * 3), max)));
            e.consume();
        });
        wv.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        return wv;
    }

    public void addPersistentNotification(String sender, String message, String color) {
        Platform.runLater(() -> {
            messageArea.getChildren().clear();
            messageArea.getChildren().add(loadOlderLabel);

            var box = new VBox(2);
            box.setPadding(new Insets(6, 8, 6, 8));
            box.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4;",
                "#252526", E_BORDER
            ));
            box.setMaxWidth(660);

            var header = new Label(sender);
            header.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 10; -fx-font-family: 'Segoe UI', 'System';", color
            ));

            var webView = createWebView(message);
            box.getChildren().addAll(header, webView);
            messageArea.getChildren().add(box);

            loadedMessageCount = 1;
            loadOlderLabel.setVisible(false);

            Platform.runLater(() -> messageScroll.setVvalue(1.0));
        });
    }

    private void loadOlderMessages() {
        var older = messageRepo.getRecent(projectPath, 10, loadedMessageCount);
        if (older.isEmpty()) return;
        Platform.runLater(() -> {
            var oldScrollPos = messageScroll.getVvalue();
            for (var msg : older) {
                var role = msg.role();
                var content = msg.content();
                var color = "assistant".equals(role) ? "#66ccff" : "#ffcc66";
                var label = "assistant".equals(role) ? Messages.get("overlay.sender.ai") : Messages.get("overlay.sender.you");
                addHistoryMessage(label, content, color);
            }
            loadedMessageCount += older.size();
            loadOlderLabel.setVisible(messageRepo.countMessages(projectPath) > loadedMessageCount);
            Platform.runLater(() -> messageScroll.setVvalue(oldScrollPos));
        });
    }

    private void addHistoryMessage(String sender, String message, String color) {
        var box = new VBox(2);
        box.setPadding(new Insets(6, 8, 6, 8));
        box.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4;",
            "#252526", E_BORDER
        ));
        box.setMaxWidth(660);

        var header = new Label(sender);
        header.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: 10; -fx-font-family: 'Segoe UI', 'System';", color
        ));

        var webView = createWebView(message);
        box.getChildren().addAll(header, webView);
        var idx = Math.max(0, messageArea.getChildren().indexOf(loadOlderLabel) + 1);
        messageArea.getChildren().add(idx, box);
    }

    public void updateStatus() {
        Platform.runLater(() -> {
            statusLabel.setText(expenseTracker.getTotalCostFormatted());
            providerLabel.setText(getProviderDisplay());
        });
    }

    private String getProviderDisplay() {
        var p = providerManager.activeProvider();
        var m = providerManager.activeModelId();
        if (p == null) return "no provider";
        return p.getName() + " / " + (m != null ? m : "?");
    }

    private void positionOverlay() {
        var screenBounds = Screen.getPrimary().getVisualBounds();
        stage.setWidth(overlayWidth);
        stage.setHeight(overlayHeight);
        stage.setX(screenBounds.getMaxX() - overlayWidth - 10);
        stage.setY(screenBounds.getMinY() + 30);
    }

    public void updateGoalDisplayFromExternal() {}

    public static final class OverlayController {
        private final OverlayApplication app;

        OverlayController(OverlayApplication app) {
            this.app = app;
        }

        public void notify(String sender, String message, String color) {
            app.addNotification(sender, message, color);
        }

        public void notifyPersistent(String sender, String message, String color) {
            app.addPersistentNotification(sender, message, color);
        }

        public void flashBinding(HotkeyAction action, boolean success) {
            app.flashBinding(action, success);
        }

        public void showConfig() { app.showConfig(); }

        public void toggleVisibility() { app.toggleVisibility(); }

        public void updateStatus() { app.updateStatus(); }

        public void updateGoalDisplay() { app.updateGoalDisplayFromExternal(); }

        public void setTaskStatus(String text, boolean running, boolean success) {
            app.setTaskStatus(text, running, success);
        }
    }
}
