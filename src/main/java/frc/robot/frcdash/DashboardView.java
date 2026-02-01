package frc.robot.frcdash;


import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.DoubleArraySubscriber;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringSubscriber;
import edu.wpi.first.networktables.StructSubscriber;
import edu.wpi.first.networktables.TimestampedObject;
import edu.wpi.first.math.geometry.Pose2d;
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
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.util.Duration;
import javafx.animation.PauseTransition;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Objects;

public final class DashboardView {
    private static final double FIELD_LENGTH_METERS = 16.54;
    private static final double FIELD_WIDTH_METERS = 8.21;
    private static final boolean FLIP_POSE_FOR_RED = false;
    private static final boolean INVERT_X = true;
    private static final boolean INVERT_Y = true;

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
    private StackPane fieldTile;

    private StructSubscriber<Pose2d> poseSub;
    private DoubleArraySubscriber poseArraySub;
    private StringSubscriber allianceSub;
    private Image fieldRedImage;
    private Image fieldBlueImage;
    private ImageView fieldImageView;
    private Pane fieldLayer;
    private Region robotMarker;

    private final Properties layoutProps = new Properties();
    private final Path layoutPath =
        Paths.get(System.getProperty("user.home"), ".frcdash", "layout.properties");

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
                if (fieldTile != null) updateFieldPose();
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

        fieldTile = buildFieldTile();

        loadLayout();

        Pane pane = new Pane();
        pane.setMinHeight(420);
        pane.getChildren().addAll(batteryTile, gyroTile, enabledTile, fieldTile);

        enableDragAndResize(batteryTile, "battery", 0, 0, 380, 160);
        enableDragAndResize(gyroTile, "gyro", 420, 0, 380, 160);
        enableDragAndResize(enabledTile, "enabled", 0, 190, 380, 160);
        enableDragAndResize(fieldTile, "field", 420, 190, 520, 300);

        StackPane wrapper = new StackPane(pane);
        StackPane.setMargin(pane, new Insets(0, 0, 0, 12));
        return wrapper;
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

    private StackPane buildFieldTile() {
        poseSub = NetworkTableInstance.getDefault()
            .getStructTopic("/AdvantageKit/RealOutputs/RobotPose", Pose2d.struct)
            .subscribe(new Pose2d());
        poseArraySub = NT.subDoubleArray("/SmartDashboard/Field/Robot", new double[] {0.0, 0.0, 0.0});
        allianceSub = NT.subString("/SmartDashboard/Alliance", "Blue");

        fieldRedImage = loadFieldImage("/field_red.png");
        fieldBlueImage = loadFieldImage("/field_blue.png");

        fieldImageView = new ImageView();
        fieldImageView.setPreserveRatio(false);

        fieldLayer = new Pane();
        fieldLayer.setPickOnBounds(false);

        robotMarker = new Region();
        robotMarker.setPrefSize(24, 24);
        robotMarker.setMinSize(16, 16);
        robotMarker.setStyle("""
            -fx-background-color: rgba(34,197,94,0.9);
            -fx-border-color: #0f172a;
            -fx-border-width: 2;
            -fx-background-radius: 4;
            -fx-border-radius: 4;
        """);

        fieldLayer.getChildren().add(robotMarker);

        StackPane fieldStack = new StackPane(fieldImageView, fieldLayer);
        fieldStack.setAlignment(Pos.CENTER);
        StackPane.setAlignment(fieldLayer, Pos.CENTER);

        StackPane tile = new StackPane(fieldStack);
        tile.setPadding(new Insets(10));
        tile.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 16;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 16;
        """);

        fieldImageView.fitWidthProperty().bind(tile.widthProperty().subtract(20));
        fieldImageView.fitHeightProperty().bind(tile.heightProperty().subtract(20));
        fieldLayer.prefWidthProperty().bind(fieldImageView.fitWidthProperty());
        fieldLayer.prefHeightProperty().bind(fieldImageView.fitHeightProperty());

        if (fieldRedImage == null && fieldBlueImage == null) {
            Label placeholder = new Label("Add field_red.png / field_blue.png to src/main/resources");
            placeholder.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12;");
            tile.getChildren().add(placeholder);
        }

        return tile;
    }

    private Image loadFieldImage(String resourcePath) {
        try {
            return new Image(Objects.requireNonNull(getClass().getResourceAsStream(resourcePath)));
        } catch (Exception ex) {
            return null;
        }
    }

    private void updateFieldPose() {
        if (fieldImageView == null || fieldLayer == null || robotMarker == null) return;

        String alliance = allianceSub != null ? allianceSub.get().trim() : "Red";
        boolean isBlue = alliance.equalsIgnoreCase("Blue");
        boolean isRed = alliance.equalsIgnoreCase("Red");
        Image desired = isBlue ? fieldBlueImage : fieldRedImage;
        if (desired == null) {
            desired = isBlue ? fieldRedImage : fieldBlueImage;
        }
        if (desired != null && fieldImageView.getImage() != desired) {
            fieldImageView.setImage(desired);
        }

        double fieldW = fieldImageView.getFitWidth();
        double fieldH = fieldImageView.getFitHeight();
        if (fieldW <= 0 || fieldH <= 0) return;

        double poseX = 0.0;
        double poseY = 0.0;
        double poseDeg = 0.0;

        TimestampedObject<Pose2d> atomic = poseSub != null ? poseSub.getAtomic() : null;
        if (atomic != null && atomic.timestamp != 0) {
            Pose2d pose = atomic.value;
            poseX = pose.getX();
            poseY = pose.getY();
            poseDeg = pose.getRotation().getDegrees();
        } else if (poseArraySub != null) {
            double[] arr = poseArraySub.get();
            if (arr.length >= 3) {
                poseX = arr[0];
                poseY = arr[1];
                poseDeg = arr[2];
            }
        }

        if (isRed && FLIP_POSE_FOR_RED) {
            poseX = FIELD_LENGTH_METERS - poseX;
            poseY = FIELD_WIDTH_METERS - poseY;
            poseDeg = (poseDeg + 180.0) % 360.0;
        }

        double nx = poseX / FIELD_LENGTH_METERS;
        double ny = poseY / FIELD_WIDTH_METERS;
        if (INVERT_X) nx = 1.0 - nx;
        if (INVERT_Y) ny = 1.0 - ny;

        if (INVERT_X) poseDeg = 180.0 - poseDeg;
        if (INVERT_Y) poseDeg = -poseDeg;

        poseDeg = ((poseDeg % 360.0) + 360.0) % 360.0;

        double xPx = nx * fieldW;
        double yPx = ny * fieldH;

        double size = Math.min(robotMarker.getPrefWidth(), robotMarker.getPrefHeight());
        robotMarker.setLayoutX(xPx - size / 2.0);
        robotMarker.setLayoutY(yPx - size / 2.0);
        robotMarker.setRotate(poseDeg);
    }

    private void enableDragAndResize(
        Region node,
        String key,
        double defaultX,
        double defaultY,
        double defaultW,
        double defaultH
    ) {
        double x = getSavedDouble(key + ".x", defaultX);
        double y = getSavedDouble(key + ".y", defaultY);
        double w = getSavedDouble(key + ".w", defaultW);
        double h = getSavedDouble(key + ".h", defaultH);
        node.setLayoutX(x);
        node.setLayoutY(y);
        node.setPrefWidth(w);
        node.setPrefHeight(h);
        node.setMinWidth(220);
        node.setMinHeight(120);

        node.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            int mask = getResizeMask(node, e.getX(), e.getY());
            int mode = mask != 0 ? 2 : 1;
            node.setUserData(new double[] {
                mode, mask, e.getSceneX(), e.getSceneY(), node.getLayoutX(), node.getLayoutY(),
                node.getPrefWidth(), node.getPrefHeight()
            });
        });

        node.setOnMouseDragged(e -> {
            if (!e.isPrimaryButtonDown()) return;
            double[] data = (double[]) node.getUserData();
            if (data == null) return;
            double dx = e.getSceneX() - data[2];
            double dy = e.getSceneY() - data[3];
            int mode = (int) data[0];
            int mask = (int) data[1];

            if (mode == 2) {
                double newX = data[4];
                double newY = data[5];
                double newW = data[6];
                double newH = data[7];

                if ((mask & 1) != 0) { // left
                    double candidate = Math.max(node.getMinWidth(), data[6] - dx);
                    newX = data[4] + (data[6] - candidate);
                    newW = candidate;
                }
                if ((mask & 2) != 0) { // right
                    newW = Math.max(node.getMinWidth(), data[6] + dx);
                }
                if ((mask & 4) != 0) { // top
                    double candidate = Math.max(node.getMinHeight(), data[7] - dy);
                    newY = data[5] + (data[7] - candidate);
                    newH = candidate;
                }
                if ((mask & 8) != 0) { // bottom
                    newH = Math.max(node.getMinHeight(), data[7] + dy);
                }

                node.setLayoutX(newX);
                node.setLayoutY(newY);
                node.setPrefWidth(newW);
                node.setPrefHeight(newH);
            } else {
                node.setLayoutX(data[4] + dx);
                node.setLayoutY(data[5] + dy);
            }
        });

        node.setOnMouseReleased(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            layoutProps.setProperty(key + ".x", Double.toString(node.getLayoutX()));
            layoutProps.setProperty(key + ".y", Double.toString(node.getLayoutY()));
            layoutProps.setProperty(key + ".w", Double.toString(node.getPrefWidth()));
            layoutProps.setProperty(key + ".h", Double.toString(node.getPrefHeight()));
            saveLayout();
        });

        node.setOnMouseMoved(e -> {
            int mask = getResizeMask(node, e.getX(), e.getY());
            node.setCursor(cursorForMask(mask));
        });

        node.setOnMouseExited(e -> node.setCursor(Cursor.DEFAULT));
    }

    private int getResizeMask(Region node, double x, double y) {
        double w = node.getWidth() > 0 ? node.getWidth() : node.getPrefWidth();
        double h = node.getHeight() > 0 ? node.getHeight() : node.getPrefHeight();
        double pad = 10;
        int mask = 0;
        if (x <= pad) mask |= 1; // left
        if (x >= w - pad) mask |= 2; // right
        if (y <= pad) mask |= 4; // top
        if (y >= h - pad) mask |= 8; // bottom
        return mask;
    }

    private Cursor cursorForMask(int mask) {
        if ((mask & 1) != 0 && (mask & 4) != 0) return Cursor.NW_RESIZE;
        if ((mask & 2) != 0 && (mask & 4) != 0) return Cursor.NE_RESIZE;
        if ((mask & 1) != 0 && (mask & 8) != 0) return Cursor.SW_RESIZE;
        if ((mask & 2) != 0 && (mask & 8) != 0) return Cursor.SE_RESIZE;
        if ((mask & 1) != 0 || (mask & 2) != 0) return Cursor.H_RESIZE;
        if ((mask & 4) != 0 || (mask & 8) != 0) return Cursor.V_RESIZE;
        return Cursor.MOVE;
    }

    private void loadLayout() {
        if (!Files.exists(layoutPath)) return;
        try (InputStream in = Files.newInputStream(layoutPath)) {
            layoutProps.load(in);
        } catch (IOException ignored) {
        }
    }

    private void saveLayout() {
        try {
            Files.createDirectories(layoutPath.getParent());
            try (OutputStream out = Files.newOutputStream(layoutPath)) {
                layoutProps.store(out, "FRC Dashboard layout");
            }
        } catch (IOException ignored) {
        }
    }

    private double getSavedDouble(String key, double fallback) {
        String v = layoutProps.getProperty(key);
        if (v == null) return fallback;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
