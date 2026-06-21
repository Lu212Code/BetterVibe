package dev.lu212.bv.indexer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class IndexingDialog {

    private final Stage stage;
    private final String projectPath;
    private Runnable onYes;
    private Runnable onNo;
    private Runnable onBasic;

    private ProgressBar progressBar;
    private Label progressLabel;
    private VBox progressContainer;
    private VBox mainContainer;

    public IndexingDialog(String projectPath, Runnable onYes, Runnable onNo, Runnable onBasic) {
        this.projectPath = projectPath;
        this.onYes = onYes;
        this.onNo = onNo;
        this.onBasic = onBasic;
        this.stage = new Stage();
    }

    public void setCallbacks(Runnable onYes, Runnable onNo, Runnable onBasic) {
        if (onYes != null) this.onYes = onYes;
        if (onNo != null) this.onNo = onNo;
        if (onBasic != null) this.onBasic = onBasic;
    }

    public void show() {
        Platform.runLater(() -> {
            stage.initStyle(StageStyle.UTILITY);
            stage.setAlwaysOnTop(true);
            stage.setTitle("BetterVibe – Projekt-Indexierung");

            mainContainer = new VBox(16);
            mainContainer.setPadding(new Insets(24));
            mainContainer.setStyle("-fx-background-color: #1a1a2e;");

            var icon = new Label("\ud83d\udd0d");
            icon.setStyle("-fx-font-size: 36;");

            var title = new Label("Projekt indexieren?");
            title.setStyle("-fx-text-fill: #e0e0ff; -fx-font-size: 18; -fx-font-weight: bold;");

            var desc = new Label(
                "Soll die KI dieses Projekt analysieren und indexieren?\n\n" +
                "Die KI durchsucht alle Quelldateien, erfasst Klassen und Methoden\n" +
                "und legt eine durchsuchbare Struktur in der Datenbank an.\n" +
                "Das beschleunigt spätere Analysen erheblich."
            );
            desc.setStyle("-fx-text-fill: #a0a0cc; -fx-font-size: 13;");
            desc.setWrapText(true);

            var projectLabel = new Label("\ud83d\udcc1 " + projectPath);
            projectLabel.setStyle("-fx-text-fill: #8888aa; -fx-font-size: 11;");
            projectLabel.setWrapText(true);

            var buttonBar = new HBox(10);
            buttonBar.setAlignment(Pos.CENTER);

            var yesBtn = new Button("Ja – Vollständig indexieren");
            yesBtn.setStyle("""
                -fx-background-color: #4444aa;
                -fx-text-fill: white;
                -fx-font-weight: bold;
                -fx-padding: 10 20;
                -fx-background-radius: 6;
                -fx-cursor: hand;
            """);

            var basicBtn = new Button("Nur Struktur (keine KI)");
            basicBtn.setStyle("""
                -fx-background-color: #2a2a4a;
                -fx-text-fill: #aaaaff;
                -fx-padding: 10 16;
                -fx-background-radius: 6;
                -fx-cursor: hand;
            """);

            var noBtn = new Button("\u00dcberspringen");
            noBtn.setStyle("""
                -fx-background-color: #333;
                -fx-text-fill: #888;
                -fx-padding: 10 16;
                -fx-background-radius: 6;
                -fx-cursor: hand;
            """);

            buttonBar.getChildren().addAll(yesBtn, basicBtn, noBtn);

            mainContainer.getChildren().addAll(icon, title, desc, projectLabel, buttonBar);

            // Progress area (hidden initially)
            progressContainer = new VBox(8);
            progressContainer.setPadding(new Insets(8, 0, 0, 0));
            progressContainer.setVisible(false);
            progressContainer.setManaged(false);

            progressLabel = new Label("Indexiere...");
            progressLabel.setStyle("-fx-text-fill: #88aaff; -fx-font-size: 12;");

            progressBar = new ProgressBar(0);
            progressBar.setPrefWidth(380);
            progressBar.setStyle("""
                -fx-accent: #6666cc;
                -fx-background-color: #2a2a4a;
            """);

            progressContainer.getChildren().addAll(progressLabel, progressBar);
            mainContainer.getChildren().add(progressContainer);

            yesBtn.setOnAction(e -> {
                showProgress();
                onYes.run();
            });

            basicBtn.setOnAction(e -> {
                showProgress();
                onBasic.run();
            });

            noBtn.setOnAction(e -> {
                stage.close();
                onNo.run();
            });

            var scene = new Scene(mainContainer, 420, 340);
            scene.setFill(Color.web("#1a1a2e"));
            stage.setScene(scene);
            stage.show();
        });
    }

    public void updateProgress(ProjectIndexer.IndexProgress progress) {
        Platform.runLater(() -> {
            double pct = (double) progress.current() / Math.max(1, progress.total());
            progressBar.setProgress(pct);
            progressLabel.setText("(" + progress.current() + "/" + progress.total() + ") " + progress.currentFile());
        });
    }

    public void showComplete(int fileCount) {
        Platform.runLater(() -> {
            progressBar.setProgress(1.0);
            progressLabel.setText("\u2705 " + fileCount + " Dateien indexiert!");
            new Thread(() -> {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> stage.close());
            }).start();
        });
    }

    public void close() {
        Platform.runLater(() -> stage.close());
    }

    private void showProgress() {
        progressContainer.setVisible(true);
        progressContainer.setManaged(true);
    }
}
