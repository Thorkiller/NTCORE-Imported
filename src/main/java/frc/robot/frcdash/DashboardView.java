package frc.robot.frcdash;


import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.NetworkTableInstance;
import frc.robot.frcdash.ui.BooleanTopicTile;
import frc.robot.frcdash.ui.DoubleTopicTile;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.util.Duration;
import javafx.animation.PauseTransition;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class DashboardView {
    private final BorderPane root = new BorderPane();

    private final Label connPill = new Label("DISCONNECTED");
    private final TextArea log = new TextArea();
    private ComboBox<Target> targetBox;
    private TextField customHost;

    // Command publisher (dashboard -> robot)
    private final BooleanPublisher zeroGyroCmd = NT.pubBool("/SmartDashboard/ZeroGyro");
    private final BooleanPublisher driveModeCmd =
        NT.pubBool("/SmartDashboard/DriveMode");

    // Editable tiles (robot -> dashboard)
    private DoubleTopicTile batteryTile;
    private DoubleTopicTile gyroTile;
    private BooleanTopicTile enabledTile;

    public DashboardView() {
        root.setPadding(new Insets(14));
        root.setStyle("""
            -fx-base: #0f172a;
            -fx-background-color: #0b1220;
            -fx-control-inner-background: #0f172a;
            -fx-text-fill: #e5e7eb;
        """);

        root.setTop(header());
        root.setLeft(null);
        root.setCenter(centerTiles());
        root.setBottom(bottomLog());

        NT.onConnectionChange(connected -> {
            setConnState(connected);
            appendLog(connected ? "Connected" : "Disconnected");
        });

        new AnimationTimer() {
            @Override public void handle(long now) {
                if (batteryTile != null) batteryTile.update();
                if (gyroTile != null) gyroTile.update();
                if (enabledTile != null) enabledTile.update();
            }
        }.start();
    }

    public Parent root() { return root; }

    // ---------------------------
    // Header
    // ---------------------------
    private Parent header() {
        Label title = new Label("FRC Dashboard");
        title.setFont(Font.font(22));
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: 700;");

        connPill.setPadding(new Insets(6, 10, 6, 10));
        connPill.setStyle(pillStyle(false));

        targetBox = new ComboBox<>(buildTargets());
        targetBox.getSelectionModel().select(0);
        targetBox.setPrefWidth(210);
        targetBox.setMaxWidth(210);

        customHost = new TextField();
        customHost.setPromptText("Enter host, ex: 10.88.66.2");
        customHost.setDisable(true);
        customHost.setPrefWidth(260);
        customHost.setMaxWidth(260);

        Runnable connectToSelected = () -> {
            Target t = targetBox.getSelectionModel().getSelectedItem();
            if (t == null) return;

            String host;
            if ("Custom...".equals(t.label())) {
                host = customHost.getText().trim();
                if (host.isEmpty()) return;
            } else {
                host = t.host();
            }

            NT.startClient(host);
            appendLog("Auto-connect: " + host);
        };

        targetBox.valueProperty().addListener((obs, oldV, newV) -> {
            boolean isCustom = newV != null && "Custom...".equals(newV.label());
            customHost.setDisable(!isCustom);

            if (!isCustom) {
                connectToSelected.run();
            } else {
                Platform.runLater(customHost::requestFocus);
            }
        });

        customHost.setOnAction(e -> connectToSelected.run());
        customHost.focusedProperty().addListener((obs, was, is) -> {
            if (!is) connectToSelected.run();
        });

        // Auto-connect to initial selection
        Platform.runLater(connectToSelected);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(12, title, spacer, targetBox, connPill);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 12, 10));

        HBox customRow = new HBox(customHost);
        customRow.setAlignment(Pos.CENTER_RIGHT);
        customRow.setPadding(new Insets(0, 10, 8, 10));
        customRow.managedProperty().bind(customHost.disabledProperty().not());
        customRow.visibleProperty().bind(customHost.disabledProperty().not());

        VBox header = new VBox(2, bar, customRow);
        return header;
    }

    private void setConnState(boolean connected) {
        connPill.setText(connected ? "CONNECTED" : "DISCONNECTED");
        connPill.setStyle(pillStyle(connected));
    }

    private String pillStyle(boolean ok) {
        String bg = ok ? "#14532d" : "#7f1d1d";
        String border = ok ? "#22c55e" : "#ef4444";
        return String.format("""
            -fx-background-color: %s;
            -fx-border-color: %s;
            -fx-border-radius: 999;
            -fx-background-radius: 999;
            -fx-text-fill: #ffffff;
            -fx-font-weight: 700;
        """, bg, border);
    }

    // ---------------------------
    // Actions panel
    // ---------------------------
    private Parent actionsPanel() {
        Label actions = new Label("Actions");
        actions.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: 700;");

        Button zeroGyro = new Button("Zero Gyro");
        zeroGyro.setMaxWidth(Double.MAX_VALUE);

        // Latch true briefly so Elastic/OutlineViewer will show it
        zeroGyro.setOnAction(e -> {
            zeroGyroCmd.set(true);
            appendLog("ZeroGyro pressed (200ms pulse)");

            PauseTransition pt = new PauseTransition(Duration.millis(200));
            pt.setOnFinished(ev -> zeroGyroCmd.set(false));
            pt.play();
        });

        Button driving = new Button("Driving");
        driving.setMaxWidth(Double.MAX_VALUE);
        driving.setOnAction(e -> {
            driveModeCmd.set(true);
            appendLog("DriveMode set true (200ms pulse)");

            PauseTransition pt = new PauseTransition(Duration.millis(200));
            pt.setOnFinished(ev -> driveModeCmd.set(false));
            pt.play();
        });

        VBox box = new VBox(10, actions, zeroGyro, driving);
        box.setPadding(new Insets(10));
        box.setPrefWidth(260);
        box.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 14;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 14;
        """);

        return box;
    }

    private record Target(String label, String host) {
        @Override public String toString() { return label; }
    }

    private ObservableList<Target> buildTargets() {
        return FXCollections.observableArrayList(
            new Target("Physical Robot (10.88.66.2)", "10.88.66.2"),
            new Target("Localhost (127.0.0.1)", "127.0.0.1"),
            new Target("Custom...", "")
        );
    }

    // ---------------------------
    // Center tiles (right-click editable topics)
    // ---------------------------
    private Parent centerTiles() {
        NetworkTableInstance inst = NetworkTableInstance.getDefault();

        batteryTile = new DoubleTopicTile(
            inst,
            "Battery",
            "Topic: /SmartDashboard/BatteryVoltage",
            "/SmartDashboard/BatteryVoltage",
            0.0,
            v -> String.format("%.2f V", v)
        );

        gyroTile = new DoubleTopicTile(
            inst,
            "Gyro",
            "Topic: /SmartDashboard/GyroDeg",
            "/SmartDashboard/GyroDeg",
            0.0,
            v -> String.format("%.1f°", v)
        );

        enabledTile = new BooleanTopicTile(
            inst,
            "Robot State",
            "Topic: /SmartDashboard/Enabled",
            "/SmartDashboard/Enabled",
            false,
            b -> b ? "ENABLED" : "DISABLED"
        );

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(0, 0, 0, 12));

        grid.add(batteryTile, 0, 0);
        grid.add(gyroTile, 1, 0);
        grid.add(enabledTile, 0, 1);

        ColumnConstraints c = new ColumnConstraints();
        c.setHgrow(Priority.ALWAYS);
        c.setFillWidth(true);
        grid.getColumnConstraints().addAll(c, c);

        return grid;
    }

    // ---------------------------
    // Bottom log
    // ---------------------------
    private Parent bottomLog() {
        log.setEditable(false);
        log.setWrapText(true);
        log.setPrefRowCount(6);
        log.setStyle("""
            -fx-control-inner-background: #0f172a;
            -fx-text-fill: #e5e7eb;
            -fx-highlight-fill: #334155;
        """);

        Label label = new Label("Log");
        label.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: 700;");

        VBox logBox = new VBox(8, label, log);
        logBox.setPadding(new Insets(12, 0, 0, 0));
        HBox.setHgrow(logBox, Priority.ALWAYS);

        HBox row = new HBox(12, logBox, actionsPanel());
        row.setAlignment(Pos.BOTTOM_LEFT);
        return row;
    }

    private void appendLog(String msg) {
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        log.appendText("[" + ts + "] " + msg + "\n");
    }
}
