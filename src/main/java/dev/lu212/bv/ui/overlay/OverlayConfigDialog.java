package dev.lu212.bv.ui.overlay;

import dev.lu212.bv.ai.AIProvider;
import dev.lu212.bv.ai.ProviderManager;
import dev.lu212.bv.config.AppConfig;
import dev.lu212.bv.db.GoalRepository;
import dev.lu212.bv.db.ProviderConfigRepository;
import dev.lu212.bv.watch.FileWatcher;
import dev.lu212.bv.i18n.Messages;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class OverlayConfigDialog {

    private final ProviderManager providerManager;
    private final GoalRepository goalRepo;
    private final ProviderConfigRepository providerConfigRepo;
    private final FileWatcher fileWatcher;
    private final AppConfig appConfig;
    private final String projectPath;
    private Stage owner;
    private Runnable onModeChanged;
    private BiConsumer<String, String> onKeySaved;
    private Runnable onConfigApplied;
    private VBox mgmtProjectList;
    private VBox mgmtKeyList;

    private static final String E_BG = "#1e1e1e";
    private static final String E_PANEL = "#252526";
    private static final String E_WIDGET = "#2d2d2d";
    private static final String E_BORDER = "#3c3c3c";
    private static final String E_TEXT = "#cccccc";
    private static final String E_TEXT_DIM = "#8a8a8a";
    private static final String E_ACCENT = "#4a7fa5";
    private static final String E_HOVER = "#2a2d2e";
    private static final String E_INPUT_BG = "#1a1a1a";

    private static String comboBoxCssUrl;
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
            var file = File.createTempFile("bettervibe-mgmt-scroll", ".css");
            file.deleteOnExit();
            Files.writeString(file.toPath(), css);
            scrollbarCssUrl = file.toURI().toURL().toString();
        } catch (IOException e) {}
    }

    private static void ensureComboCss() {
        if (comboBoxCssUrl != null) return;
        try {
            var css = """
                .combo-box-popup .list-view {
                    -fx-background-color: #1e1e1e;
                    -fx-border-color: #3c3c3c;
                    -fx-border-width: 1;
                }
                .combo-box-popup .list-cell {
                    -fx-background-color: #1e1e1e;
                    -fx-text-fill: #cccccc;
                    -fx-padding: 4 8;
                }
                .combo-box-popup .list-cell:hover {
                    -fx-background-color: #2a2d2e;
                }
                .combo-box-popup .list-cell:selected {
                    -fx-background-color: #094771;
                }
                .combo-box .list-cell {
                    -fx-background-color: #1a1a1a;
                    -fx-text-fill: #cccccc;
                    -fx-padding: 4 8;
                }
                .combo-box .arrow-button {
                    -fx-background-color: #2d2d2d;
                    -fx-border-color: #3c3c3c;
                }
                .combo-box .arrow {
                    -fx-background-color: #8a8a8a;
                }
                .combo-box .text-field {
                    -fx-background-color: #1a1a1a;
                    -fx-text-fill: #cccccc;
                    -fx-prompt-text-fill: #8a8a8a;
                }
                """;
            var file = File.createTempFile("bettervibe-combo", ".css");
            file.deleteOnExit();
            Files.writeString(file.toPath(), css);
            comboBoxCssUrl = file.toURI().toURL().toString();
        } catch (IOException e) {
            // combo boxes remain unstyled
        }
    }

    public OverlayConfigDialog(
        ProviderManager providerManager,
        GoalRepository goalRepo,
        ProviderConfigRepository providerConfigRepo,
        FileWatcher fileWatcher,
        AppConfig appConfig,
        String projectPath
    ) {
        this.providerManager = providerManager;
        this.goalRepo = goalRepo;
        this.providerConfigRepo = providerConfigRepo;
        this.fileWatcher = fileWatcher;
        this.appConfig = appConfig;
        this.projectPath = projectPath;
    }

    public void setOwner(Stage owner) {
        this.owner = owner;
    }

    public void setOnModeChanged(Runnable onModeChanged) {
        this.onModeChanged = onModeChanged;
    }

    public void setOnKeySaved(BiConsumer<String, String> onKeySaved) {
        this.onKeySaved = onKeySaved;
    }

    public void setOnConfigApplied(Runnable onConfigApplied) {
        this.onConfigApplied = onConfigApplied;
    }

    public void show() {
        Platform.runLater(() -> {
            var stage = new Stage();
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.setAlwaysOnTop(true);
            stage.setTitle("BetterVibe - Configuration");

            var root = new VBox();
            root.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1;",
                E_BG, E_BORDER
            ));

            var shadow = new DropShadow();
            shadow.setColor(Color.rgb(0, 0, 0, 0.6));
            shadow.setRadius(12);
            root.setEffect(shadow);

            var header = new HBox();
            header.setPadding(new Insets(6, 12, 6, 12));
            header.setStyle(String.format("-fx-background-color: %s; -fx-border-color: transparent transparent %s transparent; -fx-border-width: 0 0 1 0;", E_PANEL, E_BORDER));

            var title = new Label(Messages.get("config.title"));
            title.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 11; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'System';", E_TEXT));

            var closeBtn = new Button("X");
            closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + E_TEXT_DIM + "; -fx-cursor: hand; -fx-font-size: 11; -fx-padding: 1 5;");
            closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: " + E_HOVER + "; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11; -fx-padding: 1 5;"));
            closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + E_TEXT_DIM + "; -fx-cursor: hand; -fx-font-size: 11; -fx-padding: 1 5;"));
            closeBtn.setOnAction(e -> stage.close());

            var spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            header.getChildren().addAll(title, spacer, closeBtn);

            var tabBar = new HBox();
            tabBar.setPadding(new Insets(0, 12, 0, 12));
            tabBar.setStyle(String.format("-fx-background-color: %s;", E_PANEL));

            var providerTab = createTab("Provider", true);
            var goalTab = createTab("Goal && Mode", false);
            var mgmtTab = createTab("Management", false);
            var infoTab = createTab("About", false);

            tabBar.getChildren().addAll(providerTab, goalTab, mgmtTab, infoTab);

            var contentArea = new StackPane();
            contentArea.setPadding(new Insets(8, 12, 10, 12));
            contentArea.setMinHeight(220);
            contentArea.setStyle(String.format("-fx-background-color: %s;", E_BG));

            var providerContent = createProviderContent(stage);
            var goalContent = createGoalContent(stage);
            var mgmtContent = createManagementContent();
            var infoContent = createInfoContent();

            contentArea.getChildren().addAll(providerContent, goalContent, mgmtContent, infoContent);
            goalContent.setVisible(false);
            mgmtContent.setVisible(false);
            infoContent.setVisible(false);

            providerTab.setOnMouseClicked(e -> {
                setTabActive(providerTab, goalTab, mgmtTab, infoTab);
                providerContent.setVisible(true);
                goalContent.setVisible(false);
                mgmtContent.setVisible(false);
                infoContent.setVisible(false);
            });
            goalTab.setOnMouseClicked(e -> {
                setTabActive(goalTab, providerTab, mgmtTab, infoTab);
                providerContent.setVisible(false);
                goalContent.setVisible(true);
                mgmtContent.setVisible(false);
                infoContent.setVisible(false);
            });
            mgmtTab.setOnMouseClicked(e -> {
                setTabActive(mgmtTab, providerTab, goalTab, infoTab);
                providerContent.setVisible(false);
                goalContent.setVisible(false);
                mgmtContent.setVisible(true);
                infoContent.setVisible(false);
            });
            infoTab.setOnMouseClicked(e -> {
                setTabActive(infoTab, providerTab, goalTab, mgmtTab);
                providerContent.setVisible(false);
                goalContent.setVisible(false);
                mgmtContent.setVisible(false);
                infoContent.setVisible(true);
            });

            root.getChildren().addAll(header, tabBar, contentArea);

            var scene = new Scene(root, Color.TRANSPARENT);
            ensureComboCss();
            if (comboBoxCssUrl != null) {
                scene.getStylesheets().add(comboBoxCssUrl);
            }
            ensureScrollbarCss();
            if (scrollbarCssUrl != null) {
                scene.getStylesheets().add(scrollbarCssUrl);
            }
            stage.setScene(scene);
            stage.sizeToScene();
            stage.setMaxWidth(500);
            if (owner != null && owner.isShowing()) {
                stage.setX(owner.getX());
                stage.setY(owner.getY() + owner.getHeight());
            }
            stage.show();

            Platform.runLater(() -> {
                fixTextAreaStyle(goalContent);
                fixTextAreaStyle(providerContent);
            });
        });
    }

    private void fixTextAreaStyle(Parent parent) {
        for (var node : parent.lookupAll(".text-area")) {
            if (node instanceof TextArea) {
                var content = ((TextArea) node).lookup(".content");
                if (content != null) {
                    content.setStyle("-fx-background-color: " + E_INPUT_BG + ";");
                }
            }
        }
    }

    private Label createTab(String text, boolean active) {
        var tab = new Label(text);
        tab.setStyle(tabStyle(active));
        tab.setPadding(new Insets(6, 14, 6, 14));
        return tab;
    }

    private String tabStyle(boolean active) {
        if (active) {
            return String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System'; -fx-padding: 6 14; -fx-border-color: transparent transparent %s transparent; -fx-border-width: 0 0 2 0; -fx-cursor: hand;",
                E_BG, E_ACCENT
            );
        }
        return String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System'; -fx-padding: 6 14; -fx-border-color: transparent; -fx-cursor: hand;",
            E_PANEL, E_TEXT_DIM
        );
    }

    private void setTabActive(Label active, Label... rest) {
        active.setStyle(tabStyle(true));
        for (var t : rest) t.setStyle(tabStyle(false));
    }

    private String inputStyle() {
        return String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-prompt-text-fill: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-padding: 6; -fx-font-size: 12; -fx-font-family: 'Segoe UI', 'System';",
            E_INPUT_BG, E_TEXT, E_TEXT_DIM, E_BORDER
        );
    }

    private String labelStyle() {
        return String.format(
            "-fx-text-fill: %s; -fx-font-size: 11; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'System'; -fx-padding: 4 0 2 0;",
            E_TEXT
        );
    }

    private VBox createProviderContent(Stage stage) {
        var box = new VBox(6);

        var providerLabel = new Label(Messages.get("config.provider.label"));
        providerLabel.setStyle(labelStyle());

        var providerCombo = new ComboBox<String>();
        providerCombo.setStyle(inputStyle());
        providerCombo.setPrefWidth(Double.MAX_VALUE);
        providerCombo.getItems().addAll(providerManager.getProviderNames());
        var active = providerManager.activeProvider();
        if (active != null) providerCombo.setValue(active.getName());

        var apiKeyLabel = new Label(Messages.get("config.provider.apikey"));
        apiKeyLabel.setStyle(labelStyle());

        var apiKeyField = new PasswordField();
        apiKeyField.setStyle(inputStyle());
        apiKeyField.setPromptText(Messages.get("config.provider.apikey.prompt"));
        if (active != null) {
            providerConfigRepo.getApiKey(active.getName()).ifPresent(apiKeyField::setText);
        }

        var modelLabel = new Label(Messages.get("config.provider.model"));
        modelLabel.setStyle(labelStyle());

        var modelCombo = new ComboBox<String>();
        modelCombo.setStyle(inputStyle());
        modelCombo.setPrefWidth(Double.MAX_VALUE);

        var providerStatusLabel = new Label();
        providerStatusLabel.setStyle("-fx-font-size: 10; -fx-padding: 2 0;");

        loadModelsForProvider(providerCombo.getValue(), modelCombo, providerStatusLabel);

        providerCombo.setOnAction(e -> {
            var name = providerCombo.getValue();
            if (name != null) {
                providerConfigRepo.getApiKey(name).ifPresentOrElse(
                    apiKeyField::setText,
                    () -> apiKeyField.clear()
                );
                loadModelsForProvider(name, modelCombo, providerStatusLabel);
            }
        });

        var testBtn = new Button(Messages.get("config.provider.test"));
        testBtn.setStyle(String.format(
            "-fx-background-color: #264f78; -fx-text-fill: white; -fx-padding: 5 14; -fx-cursor: hand; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System'; -fx-border-color: %s; -fx-border-width: 1;",
            E_ACCENT
        ));

        var buttonBar = new HBox(8);
        buttonBar.setPadding(new Insets(8, 0, 0, 0));

        var saveBtn = new Button(Messages.get("config.provider.apply"));
        saveBtn.setStyle(String.format(
            "-fx-background-color: #0e639c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 18; -fx-cursor: hand; -fx-font-size: 12; -fx-font-family: 'Segoe UI', 'System'; -fx-border-color: %s; -fx-border-width: 1;",
            E_ACCENT
        ));
        saveBtn.setOnMouseEntered(e -> saveBtn.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 18; -fx-cursor: hand; -fx-font-size: 12; -fx-font-family: 'Segoe UI', 'System'; -fx-border-color: %s; -fx-border-width: 1;",
            E_ACCENT, E_ACCENT
        )));
        saveBtn.setOnMouseExited(e -> saveBtn.setStyle(String.format(
            "-fx-background-color: #0e639c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 18; -fx-cursor: hand; -fx-font-size: 12; -fx-font-family: 'Segoe UI', 'System'; -fx-border-color: %s; -fx-border-width: 1;",
            E_ACCENT
        )));

        var cancelBtn = new Button(Messages.get("config.provider.cancel"));
        cancelBtn.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-padding: 6 18; -fx-cursor: hand; -fx-font-size: 12; -fx-font-family: 'Segoe UI', 'System'; -fx-border-color: %s; -fx-border-width: 1;",
            E_WIDGET, E_TEXT, E_BORDER
        ));
        cancelBtn.setOnAction(e -> stage.close());

        buttonBar.getChildren().addAll(saveBtn, cancelBtn, testBtn);

        box.getChildren().addAll(
            providerLabel, providerCombo,
            apiKeyLabel, apiKeyField,
            modelLabel, modelCombo,
            providerStatusLabel,
            buttonBar
        );

        saveBtn.setOnAction(e -> {
            var providerName = providerCombo.getValue();
            var modelId = modelCombo.getValue();
            var apiKey = apiKeyField.getText();

            if (providerName != null) {
                if (apiKey != null && !apiKey.isBlank()) {
                    providerConfigRepo.save(providerName, apiKey, null);
                    if (onKeySaved != null) onKeySaved.accept(providerName, apiKey);
                }
                providerManager.setActiveProvider(providerName);
                if (modelId != null) {
                    providerManager.setActiveModel(modelId);
                }
                goalRepo.setProvider(projectPath, providerName, modelId);
            }
            stage.close();
            if (onConfigApplied != null) onConfigApplied.run();
        });

        testBtn.setOnAction(e -> {
            testBtn.setText(Messages.get("config.provider.testing"));
            testBtn.setDisable(true);
            var providerName = providerCombo.getValue();
            var apiKey = apiKeyField.getText();
            if (providerName != null && apiKey != null && !apiKey.isBlank()) {
                providerConfigRepo.save(providerName, apiKey, null);
                if (onKeySaved != null) onKeySaved.accept(providerName, apiKey);
                providerManager.setActiveProvider(providerName);
            }
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    Platform.runLater(() -> {
                        testBtn.setText(Messages.get("config.provider.ok"));
                        testBtn.setStyle("-fx-background-color: #1b3b1b; -fx-text-fill: #6a9955; -fx-padding: 5 14; -fx-cursor: hand; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        testBtn.setText(Messages.get("config.provider.failed"));
                        testBtn.setStyle("-fx-background-color: #3b1b1b; -fx-text-fill: #c75a5a; -fx-padding: 5 14; -fx-cursor: hand; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';");
                    });
                }
                Platform.runLater(() -> testBtn.setDisable(false));
            }).start();
        });

        return box;
    }

    private VBox createGoalContent(Stage stage) {
        var box = new VBox(6);

        var goalLabel = new Label(Messages.get("config.goal.label"));
        goalLabel.setStyle(labelStyle());

        var goalField = new TextArea();
        goalField.setStyle(inputStyle());
        goalField.setPromptText(Messages.get("config.goal.prompt"));
        goalField.setPrefRowCount(2);
        goalField.setWrapText(true);
        goalRepo.getGoal(projectPath).ifPresent(goalField::setText);

        var modeLabel = new Label(Messages.get("config.mode.label"));
        modeLabel.setStyle(labelStyle());

        var modeCombo = new ComboBox<String>();
        modeCombo.setStyle(inputStyle());
        modeCombo.setPrefWidth(Double.MAX_VALUE);
        modeCombo.getItems().addAll("manual", "auto");
        var currentMode = goalRepo.getMode(projectPath).orElse("manual");
        modeCombo.setValue(currentMode);

        var modeInfo = new Label(Messages.get("config.mode.info"));
        modeInfo.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';", E_TEXT_DIM));
        modeInfo.setWrapText(true);

        var langLabel = new Label(Messages.get("config.lang.label"));
        langLabel.setStyle(labelStyle());

        var langCombo = new ComboBox<String>();
        langCombo.setStyle(inputStyle());
        langCombo.setPrefWidth(Double.MAX_VALUE);
        langCombo.getItems().addAll("en", "de");
        langCombo.setValue(Messages.currentLang());

        langCombo.setOnAction(ev -> {
            var newLang = langCombo.getValue();
            if (newLang != null && !newLang.equals(Messages.currentLang())) {
                Messages.init(newLang);
                appConfig.set("lang", newLang);
                appConfig.save();
            }
        });

        var sizeLabel = new Label(Messages.get("config.size.label"));
        sizeLabel.setStyle(labelStyle());

        var sizeRow = new HBox(8);
        sizeRow.setAlignment(Pos.CENTER_LEFT);
        var widthField = new TextField(String.valueOf(appConfig.getInt("overlay_width", 680)));
        widthField.setStyle(inputStyle());
        widthField.setPrefWidth(70);
        var heightField = new TextField(String.valueOf(appConfig.getInt("overlay_height", 360)));
        heightField.setStyle(inputStyle());
        heightField.setPrefWidth(70);
        sizeRow.getChildren().addAll(new Label(Messages.get("config.size.w")), widthField, new Label(Messages.get("config.size.h")), heightField);

        var buttonBar = new HBox(8);
        buttonBar.setPadding(new Insets(8, 0, 0, 0));

        var saveBtn = new Button(Messages.get("config.goal.apply"));
        saveBtn.setStyle(String.format(
            "-fx-background-color: #0e639c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 18; -fx-cursor: hand; -fx-font-size: 12; -fx-font-family: 'Segoe UI', 'System'; -fx-border-color: %s; -fx-border-width: 1;",
            E_ACCENT
        ));

        var cancelBtn = new Button(Messages.get("config.goal.cancel"));
        cancelBtn.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-padding: 6 18; -fx-cursor: hand; -fx-font-size: 12; -fx-font-family: 'Segoe UI', 'System'; -fx-border-color: %s; -fx-border-width: 1;",
            E_WIDGET, E_TEXT, E_BORDER
        ));
        cancelBtn.setOnAction(e -> stage.close());

        buttonBar.getChildren().addAll(saveBtn, cancelBtn);

        box.getChildren().addAll(
            goalLabel, goalField,
            modeLabel, modeCombo, modeInfo,
            langLabel, langCombo,
            sizeLabel, sizeRow,
            buttonBar
        );

        saveBtn.setOnAction(e -> {
            var goalText = goalField.getText();
            var mode = modeCombo.getValue();
            if (goalText != null && !goalText.isBlank()) {
                goalRepo.setGoal(projectPath, goalText);
            }
            if (mode != null) {
                goalRepo.setMode(projectPath, mode);
                if (onModeChanged != null) onModeChanged.run();
            }
            try {
                var w = Integer.parseInt(widthField.getText().strip());
                var h = Integer.parseInt(heightField.getText().strip());
                if (w >= 400 && h >= 120) {
                    appConfig.setInt("overlay_width", w);
                    appConfig.setInt("overlay_height", h);
                    appConfig.save();
                }
            } catch (NumberFormatException ex) {}
            stage.close();
        });

        return box;
    }

    private VBox createInfoContent() {
        var box = new VBox(6);

        var version = new Label(Messages.get("config.about.version"));
        version.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 14; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'System';", E_TEXT));

        var desc = new Label(Messages.get("config.about.desc"));
        desc.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';", E_TEXT_DIM));
        desc.setWrapText(true);

        var stats = new Label();
        stats.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 11; -fx-font-family: 'Segoe UI', 'System';", E_TEXT_DIM));
        var p = providerManager.activeProvider();
        var model = providerManager.activeModelId();
        stats.setText(Messages.get("config.about.stats",
            p != null ? p.getName() : "-",
            model != null ? model : "-",
            projectPath,
            appConfig.getInt("overlay_width", 680) + "x" + appConfig.getInt("overlay_height", 360)));

        box.getChildren().addAll(version, desc, stats);
        return box;
    }

    private void loadModelsForProvider(String name, ComboBox<String> modelCombo, Label statusLabel) {
        if (name == null) return;
        statusLabel.setText("");
        if ("ollama".equals(name)) {
            modelCombo.getItems().clear();
            modelCombo.setPromptText(Messages.get("ollama.checking"));
            new Thread(() -> {
                var provider = providerManager.getProvider(name);
                if (provider == null) return;
                var available = provider.isAvailable();
                var models = provider.getModels();
                Platform.runLater(() -> {
                    modelCombo.getItems().clear();
                    for (var m : models) modelCombo.getItems().add(m.id());
                    if (!modelCombo.getItems().isEmpty())
                        modelCombo.setValue(modelCombo.getItems().get(0));
                    modelCombo.setPromptText(Messages.get("ollama.model.prompt"));
                    if (available) {
                        statusLabel.setText(Messages.get("ollama.connected", models.size()));
                        statusLabel.setStyle("-fx-text-fill: #6a9955; -fx-font-size: 10;");
                    } else {
                        statusLabel.setText(Messages.get("ollama.unreachable"));
                        statusLabel.setStyle("-fx-text-fill: #c75a5a; -fx-font-size: 10;");
                    }
                });
            }).start();
        } else {
            updateModels(name, modelCombo);
        }
    }

    private VBox createManagementContent() {
        var box = new VBox(6);
        box.visibleProperty().addListener((obs, old, v) -> {
            if (v && mgmtProjectList != null && mgmtKeyList != null) {
                Platform.runLater(() -> {
                    refreshProjectList(mgmtProjectList);
                    refreshKeyList(mgmtKeyList);
                });
            }
        });

        var projectHeader = new Label(Messages.get("mgmt.projects.header"));
        projectHeader.setStyle(labelStyle());

        mgmtProjectList = new VBox(4);
        refreshProjectList(mgmtProjectList);

        var projectScroll = new ScrollPane(mgmtProjectList);
        projectScroll.setFitToWidth(true);
        projectScroll.setPrefHeight(130);
        projectScroll.setStyle(String.format(
            "-fx-background-color: %s; -fx-background: %s; -fx-border-color: %s; -fx-border-width: 1;",
            E_INPUT_BG, E_INPUT_BG, E_BORDER
        ));

        var sep = new Region();
        sep.setPrefHeight(1);
        sep.setStyle("-fx-background-color: " + E_BORDER + ";");

        var keyHeader = new Label(Messages.get("mgmt.keys.header"));
        keyHeader.setStyle(labelStyle());

        mgmtKeyList = new VBox(4);
        refreshKeyList(mgmtKeyList);
        var addKeyRow = new HBox(6);
        addKeyRow.setAlignment(Pos.CENTER_LEFT);
        var addKeyCombo = new ComboBox<String>();
        addKeyCombo.setStyle(inputStyle());
        addKeyCombo.setPrefWidth(90);
        addKeyCombo.setEditable(true);
        addKeyCombo.getItems().addAll(providerManager.getProviderNames());
        var addKeyField = new PasswordField();
        addKeyField.setStyle(inputStyle());
        addKeyField.setPromptText(Messages.get("mgmt.keys.key.prompt"));
        addKeyField.setPrefWidth(140);
        addKeyField.setPrefHeight(22);
        var addKeyBtn = new Button(Messages.get("mgmt.keys.add"));
        addKeyBtn.setStyle(String.format(
            "-fx-background-color: #264f78; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 2 8; -fx-cursor: hand;",
            E_BORDER
        ));
        addKeyBtn.setOnAction(ev -> {
            var name = addKeyCombo.getValue();
            var key = addKeyField.getText();
            if (name != null && !name.isBlank() && key != null && !key.isBlank()) {
                providerConfigRepo.save(name, key, null);
                addKeyField.clear();
                refreshKeyList(mgmtKeyList);
            }
        });
        addKeyRow.getChildren().addAll(addKeyCombo, addKeyField, addKeyBtn);

        var keyScroll = new ScrollPane(mgmtKeyList);
        keyScroll.setFitToWidth(true);
        keyScroll.setPrefHeight(130);
        keyScroll.setStyle(String.format(
            "-fx-background-color: %s; -fx-background: %s; -fx-border-color: %s; -fx-border-width: 1;",
            E_INPUT_BG, E_INPUT_BG, E_BORDER
        ));

        box.getChildren().addAll(projectHeader, projectScroll, sep, keyHeader, addKeyRow, keyScroll);
        return box;
    }

    private void refreshProjectList(VBox list) {
        list.getChildren().clear();
        var projects = goalRepo.getAllProjects();
        if (projects.isEmpty()) {
            var empty = new Label(Messages.get("mgmt.projects.empty"));
            empty.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 11; -fx-padding: 8;", E_TEXT_DIM));
            list.getChildren().add(empty);
            return;
        }
        for (var p : projects) {
            var card = new VBox(2);
            card.setPadding(new Insets(6, 8, 6, 8));
            card.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-background-radius: 3; -fx-border-radius: 3;",
                E_WIDGET, E_BORDER
            ));

            var pathRow = new HBox(6);
            pathRow.setAlignment(Pos.CENTER_LEFT);
            var pathLabel = new Label(p.projectPath());
            pathLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10; -fx-font-weight: bold;");
            pathLabel.setMaxWidth(300);
            var modeLabel = new Label(p.mode() != null ? p.mode() : "manual");
            modeLabel.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 9; -fx-padding: 1 4; -fx-background-color: #1a3a1a; -fx-background-radius: 2;",
                "#6a9955"
            ));
            pathRow.getChildren().addAll(pathLabel, modeLabel);

            var goalField = new TextField(p.goal() != null ? p.goal() : "");
            goalField.setStyle(inputStyle());
            goalField.setPromptText(Messages.get("mgmt.projects.goal.prompt"));
            goalField.setPrefHeight(24);

            var saveGoalBtn = new Button(Messages.get("mgmt.projects.save"));
            saveGoalBtn.setStyle(String.format(
                "-fx-background-color: #264f78; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 2 10; -fx-cursor: hand;",
                E_BORDER
            ));
            saveGoalBtn.setOnAction(ev -> {
                goalRepo.setGoal(p.projectPath(), goalField.getText());
                saveGoalBtn.setText("✓");
            });
            saveGoalBtn.setVisible(false);

            goalField.textProperty().addListener((obs, old, val) -> {
                saveGoalBtn.setVisible(!val.equals(p.goal() != null ? p.goal() : ""));
            });

            var delProjectBtn = new Button("✕");
            delProjectBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #c75a5a; -fx-font-size: 11; -fx-cursor: hand; -fx-padding: 2 4;");
            delProjectBtn.setOnAction(ev -> {
                var dialog = new Stage();
                dialog.initStyle(StageStyle.TRANSPARENT);
                dialog.setAlwaysOnTop(true);
                var root = new VBox(8);
                root.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-padding: 14; -fx-background-radius: 6; -fx-border-radius: 6;", E_PANEL, E_BORDER));
                var shadow = new DropShadow();
                shadow.setColor(Color.rgb(0, 0, 0, 0.6));
                shadow.setRadius(12);
                root.setEffect(shadow);
                var msg = new Label(Messages.get("mgmt.projects.delete.confirm", p.projectPath()));
                msg.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 11;", E_TEXT));
                msg.setWrapText(true);
                var hint = new Label(Messages.get("mgmt.projects.delete.hint"));
                hint.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 10;", E_TEXT_DIM));
                hint.setWrapText(true);
                var btnRow = new HBox(8);
                btnRow.setAlignment(Pos.CENTER_RIGHT);
                var yesBtn = new Button(Messages.get("mgmt.projects.delete"));
                yesBtn.setStyle("-fx-background-color: #5a1a1a; -fx-text-fill: #ff6666; -fx-font-size: 11; -fx-padding: 4 14; -fx-cursor: hand;");
                yesBtn.setOnAction(e -> {
                    goalRepo.delete(p.projectPath());
                    refreshProjectList(list);
                    dialog.close();
                });
                var noBtn = new Button(Messages.get("mgmt.projects.cancel"));
                noBtn.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 11; -fx-padding: 4 14; -fx-cursor: hand; -fx-border-color: %s; -fx-border-width: 1;", E_WIDGET, E_TEXT, E_BORDER));
                noBtn.setOnAction(e -> dialog.close());
                btnRow.getChildren().addAll(noBtn, yesBtn);
                root.getChildren().addAll(msg, hint, btnRow);
                var scene = new Scene(root, Color.TRANSPARENT);
                dialog.setScene(scene);
                dialog.sizeToScene();
                var bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
                dialog.setX(bounds.getMinX() + (bounds.getWidth() - dialog.getWidth()) / 2);
                dialog.setY(bounds.getMinY() + (bounds.getHeight() - dialog.getHeight()) / 2);
                dialog.show();
            });

            var goalRow = new HBox(6);
            goalRow.setAlignment(Pos.CENTER_LEFT);
            goalRow.getChildren().addAll(goalField, saveGoalBtn, delProjectBtn);

            card.getChildren().addAll(pathRow, goalRow);
            list.getChildren().add(card);
        }
    }

    private void refreshKeyList(VBox list) {
        list.getChildren().clear();
        var entries = providerConfigRepo.getAll();
        if (entries.isEmpty()) {
            var empty = new Label(Messages.get("mgmt.keys.empty"));
            empty.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 11; -fx-padding: 8;", E_TEXT_DIM));
            list.getChildren().add(empty);
            return;
        }
        for (var e : entries) {
            var card = new HBox(6);
            card.setPadding(new Insets(6, 8, 6, 8));
            card.setAlignment(Pos.CENTER_LEFT);
            card.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1; -fx-background-radius: 3; -fx-border-radius: 3;",
                E_WIDGET, E_BORDER
            ));

            var nameLabel = new Label(e.name());
            nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10; -fx-font-weight: bold;");
            nameLabel.setPrefWidth(60);

            var keyField = new PasswordField();
            keyField.setStyle(inputStyle());
            keyField.setText(e.apiKey() != null ? e.apiKey() : "");
            keyField.setPrefWidth(140);
            keyField.setPrefHeight(22);

            var activeLabel = new Label(e.active() ? Messages.get("mgmt.keys.active") : "");
            activeLabel.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 9; -fx-padding: 1 4; -fx-background-color: %s; -fx-background-radius: 2;",
                e.active() ? "#6a9955" : E_TEXT_DIM, e.active() ? "#1a3a1a" : E_WIDGET
            ));

            var saveKeyBtn = new Button(Messages.get("mgmt.keys.save"));
            saveKeyBtn.setStyle("-fx-background-color: #264f78; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 2 8; -fx-cursor: hand;");

            var delBtn = new Button("✕");
            delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #c75a5a; -fx-font-size: 11; -fx-cursor: hand; -fx-padding: 2 4;");

            saveKeyBtn.setOnAction(ev -> {
                providerConfigRepo.save(e.name(), keyField.getText(), e.baseUrl());
                saveKeyBtn.setText("✓");
            });

            delBtn.setOnAction(ev -> {
                providerConfigRepo.delete(e.name());
                refreshKeyList(list);
            });

            card.getChildren().addAll(nameLabel, keyField, activeLabel, saveKeyBtn, delBtn);
            list.getChildren().add(card);
        }
    }

    private void updateModels(String providerName, ComboBox<String> modelCombo) {
        modelCombo.getItems().clear();
        if (providerName == null) return;
        var provider = providerManager.getProvider(providerName);
        if (provider == null) return;
        for (var m : provider.getModels()) {
            modelCombo.getItems().add(m.id());
        }
        var active = providerManager.activeModelId();
        if (active != null && modelCombo.getItems().contains(active)) {
            modelCombo.setValue(active);
        } else if (!modelCombo.getItems().isEmpty()) {
            modelCombo.setValue(modelCombo.getItems().get(0));
        }
    }
}
