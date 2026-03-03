package frc.robot.frcdash;


import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.DoubleArraySubscriber;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringSubscriber;
import edu.wpi.first.networktables.StructSubscriber;
import edu.wpi.first.networktables.TimestampedObject;
import edu.wpi.first.math.geometry.Pose2d;
import frc.robot.frcdash.ui.BooleanTopicTile;
import frc.robot.frcdash.ui.DoubleTopicTile;
import javafx.animation.AnimationTimer;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
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
import javafx.geometry.Rectangle2D;
import javafx.scene.shape.Rectangle;

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
import java.util.ArrayList;
import java.util.List;

public final class DashboardView {
    private static final double FIELD_LENGTH_METERS = 16.54;
    private static final double FIELD_WIDTH_METERS = 8.21;
    private static final double FIELD_X_MIN_METERS = 0.381;
    private static final double FIELD_X_MAX_METERS = 16.161;
    private static final double FIELD_Y_MIN_METERS = 0.380;
    private static final double FIELD_Y_MAX_METERS = 7.673;
    private static final double ROBOT_SIZE_METERS = 0.762; // 30 in
    private static final double GAME_PIECE_DIAMETER_METERS = 0.150114; // 5.91 in
    private static final double AUTO_SECONDS = 20.0;
    private static final double TRANSITION_SECONDS = 10.0;
    private static final double SHIFT_SECONDS = 25.0;
    private static final int SHIFT_COUNT = 4;
    private static final double ENDGAME_SECONDS = 30.0;
    private static final double TELEOP_SECONDS =
        TRANSITION_SECONDS + (SHIFT_SECONDS * SHIFT_COUNT) + ENDGAME_SECONDS;
    private static final double MATCH_SECONDS = AUTO_SECONDS + TELEOP_SECONDS;
    private static final double SWITCH_WARNING_SECONDS = 8.0;
    private static final double GREEN_DELAY_SECONDS = 2.0;
    private static final double FIELD_IMAGE_WIDTH_PX = 806.0;
    private static final double FIELD_IMAGE_HEIGHT_PX = 388.0;
    private static final boolean FLIP_POSE_FOR_RED = false;
    private static final boolean INVERT_X = true;
    private static final boolean INVERT_Y = false;
    private static final double SHOOTER_RPM_TOLERANCE = 50.0;

    private final BorderPane root = new BorderPane();
    private final StackPane tabContent = new StackPane();
    private final ToggleGroup tabGroup = new ToggleGroup();
    private ToggleButton dashboardTabButton;
    private ToggleButton shooterTabButton;
    private Parent dashboardContent;
    private Parent shooterContent;

    private final Label connPill = new Label("DISCONNECTED");
    private final TextArea log = new TextArea();
    private ComboBox<Target> targetBox;
    private TextField customHost;

    // Command publisher (dashboard -> robot)
    private final BooleanPublisher zeroGyroCmd = NT.pubBool("/Dashboard/ZeroGyro");
    private final BooleanPublisher driveModeCmd =
        NT.pubBool("/Dashboard/DriveMode");
    private final BooleanPublisher shootCmd = NT.pubBool("/Dashboard/Shoot");
    private final BooleanPublisher intakeCmd = NT.pubBool("/Dashboard/Intake");
    private final BooleanPublisher intakeInCmd = NT.pubBool("/Dashboard/IntakeIn");
    private final BooleanPublisher hasPieceCmd = NT.pubBool("/Dashboard/HasPiece");
    private final DoublePublisher shooterTargetCmd = NT.pubDouble("/Dashboard/ShooterTargetRPM");
    private final BooleanPublisher hoodAngleCmd = NT.pubBool("/Dashboard/HoodAngleDeg");
    private final DoublePublisher hoodSetpointCmd = NT.pubDouble("/Dashboard/HoodSetpointDeg");
    private final BooleanPublisher spindexerCmd = NT.pubBool("/Dashboard/Spindexer");
    private final BooleanPublisher spindexerReverseCmd = NT.pubBool("/Dashboard/SpindexerReverse");

    // Editable tiles (robot -> dashboard)
    private DoubleTopicTile batteryTile;
    private DoubleTopicTile gyroTile;
    private BooleanTopicTile enabledTile;
    private StackPane fieldTile;
    private StackPane matchTimerTile;
    private Button zeroGyroTile;
    private Button driveModeTile;
    private Button shootTile;
    private Button intakeTile;
    private Button intakeInTile;
    private Button hasPieceTile;
    private Button spindexerReverseTile;
    private Button hoodDownTile;
    private StackPane shooterTile;
    private StackPane boostTile;
    private Slider shooterBoostSlider;
    private final Label boostValueLabel = new Label("Boost: +0 RPM");
    private final Label shooterStatusLabel = new Label("NOT READY");
    private final Label shooterActualLabel = new Label("Actual: -- RPM");
    private final Label shooterTargetLabel = new Label("Target: -- RPM");
    private final Label matchTimerLabel = new Label("02:15");
    private final Label hubStatusLabel = new Label("HUB: --");
    private final Label hubCountdownLabel = new Label("--");
    private final Label hubAllianceLabel = new Label("--");
    private javafx.stage.Stage logStage;

    private final DoubleSubscriber matchTimeSub = NT.subDouble("/AdvantageKit/RealOutputs/MatchTime", 0.0);
    private final StringSubscriber autoWinnerSub = NT.subString("/AdvantageKit/RealOutputs/AutoWinner", "");
    private final DoubleSubscriber shooterRpmSub = NT.subDouble("/Dashboard/ShooterRPM", 0.0);
    private final DoubleSubscriber shooterTargetSub = NT.subDouble("/Dashboard/ShooterTargetRPM", 0.0);
    private StructSubscriber<Pose2d> poseSub;
    private DoubleArraySubscriber poseArraySub;
    private StringSubscriber allianceSub;
    private DoubleArraySubscriber gamePieceSub;
    private Image fieldFullImage;
    private ImageView fieldImageView;
    private Pane fieldLayer;
    private Region robotMarker;
    private Pane gamePieceLayer;
    private final List<Region> gamePieceMarkers = new ArrayList<>();
    private double lastFieldW = -1.0;
    private double lastFieldH = -1.0;
    private String lastAlliance = "";

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
        root.setCenter(buildTabbedCenter());
        root.setBottom(null);

        setupLogWindow();

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
                updateMatchTimer();
                updateHubTimer();
                updateShooterStatus();
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

        HBox tabs = buildTabBar();
        tabs.setPadding(new Insets(0, 10, 8, 10));

        HBox customRow = new HBox(customHost);
        customRow.setAlignment(Pos.CENTER_RIGHT);
        customRow.setPadding(new Insets(0, 10, 8, 10));
        customRow.managedProperty().bind(customHost.disabledProperty().not());
        customRow.visibleProperty().bind(customHost.disabledProperty().not());

        VBox header = new VBox(2, bar, tabs, customRow);
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
            "Topic: /Dashboard/BatteryVoltage",
            "/Dashboard/BatteryVoltage",
            0.0,
            v -> String.format("%.2f V", v)
        );

        gyroTile = new DoubleTopicTile(
            inst,
            "Gyro",
            "Topic: /Dashboard/GyroDeg",
            "/Dashboard/GyroDeg",
            0.0,
            v -> String.format("%.1f°", v)
        );

        enabledTile = new BooleanTopicTile(
            inst,
            "Robot State",
            "Topic: /Dashboard/Enabled",
            "/Dashboard/Enabled",
            false,
            b -> b ? "ENABLED" : "DISABLED"
        );

        fieldTile = buildFieldTile();
        matchTimerTile = buildMatchTimerTile();
        zeroGyroTile = buildActionTile(
            "Zero Gyro",
            zeroGyroCmd,
            "ZeroGyro pressed (200ms pulse)"
        );
        driveModeTile = buildActionTile(
            "Driving",
            driveModeCmd,
            "DriveMode set true (200ms pulse)"
        );
        shootTile = buildActionTile(
            "Shoot",
            shootCmd,
            "Shoot pressed (200ms pulse)"
        );
        intakeTile = buildActionTile(
            "Intake",
            intakeCmd,
            "Intake pressed (200ms pulse)"
        );
        hasPieceTile = buildActionTile(
            "Has Piece",
            hasPieceCmd,
            "HasPiece pressed (200ms pulse)"
        );
        shooterTile = buildShooterTile();
        boostTile = buildBoostTile();
        spindexerReverseTile = buildActionTile(
            "Spindexer Reverse",
            spindexerReverseCmd,
            "Spindexer reverse pressed (200ms pulse)"
        );
        hoodDownTile = buildActionTile(
            "Hood Down",
            hoodAngleCmd,
            "Hood down pressed (200ms pulse)"
        );
        intakeInTile = buildActionTile("Intake In", () -> {
            intakeInCmd.set(true);
            appendLog("IntakeIn pressed (200ms pulse)");

            PauseTransition pt = new PauseTransition(Duration.millis(200));
            pt.setOnFinished(ev -> intakeInCmd.set(false));
            pt.play();
        });

        loadLayout();

        Pane pane = new Pane();
        pane.setMinHeight(980);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(pane.widthProperty());
        clip.heightProperty().bind(pane.heightProperty());
        pane.setClip(clip);
        pane.getChildren().addAll(
            batteryTile, gyroTile, enabledTile, fieldTile, matchTimerTile,
            zeroGyroTile, driveModeTile, shootTile, intakeTile, hasPieceTile, shooterTile, boostTile,
            spindexerReverseTile, hoodDownTile, intakeInTile);

        enableDragAndResize(batteryTile, "battery", 0, 0, 380, 160);
        enableDragAndResize(gyroTile, "gyro", 420, 0, 380, 160);
        enableDragAndResize(enabledTile, "enabled", 0, 190, 380, 160);
        enableDragAndResize(fieldTile, "field", 420, 190, 520, 300);
        enableDragAndResize(matchTimerTile, "matchTimer", 0, 360, 240, 120);
        enableDragAndResize(zeroGyroTile, "zeroGyro", 260, 360, 240, 120);
        enableDragAndResize(driveModeTile, "driveMode", 520, 360, 240, 120);
        enableDragAndResize(shootTile, "shoot", 260, 500, 240, 120);
        enableDragAndResize(intakeTile, "intake", 520, 500, 240, 120);
        enableDragAndResize(hasPieceTile, "hasPiece", 0, 500, 240, 120);
        enableDragAndResize(shooterTile, "shooter", 0, 640, 520, 160);
        enableDragAndResize(boostTile, "boost", 560, 640, 340, 160);
        enableDragAndResize(spindexerReverseTile, "spindexerReverse", 0, 820, 240, 120);
        enableDragAndResize(hoodDownTile, "hoodDown", 260, 820, 240, 120);
        enableDragAndResize(intakeInTile, "intakeIn", 520, 820, 240, 120);

        StackPane wrapper = new StackPane(pane);
        StackPane.setMargin(pane, new Insets(0, 0, 0, 12));
        return wrapper;
    }

    private Parent buildTabbedCenter() {
        dashboardContent = centerTiles();
        shooterContent = buildShooterControlTab();

        tabContent.getChildren().addAll(dashboardContent, shooterContent);
        selectTab("Dashboard");
        return tabContent;
    }

    private HBox buildTabBar() {
        dashboardTabButton = buildTabButton("Dashboard");
        shooterTabButton = buildTabButton("Shooter");
        dashboardTabButton.setToggleGroup(tabGroup);
        shooterTabButton.setToggleGroup(tabGroup);
        dashboardTabButton.setSelected(true);

        tabGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == dashboardTabButton) {
                selectTab("Dashboard");
            } else if (newV == shooterTabButton) {
                selectTab("Shooter");
            }
        });

        HBox tabs = new HBox(8, dashboardTabButton, shooterTabButton);
        tabs.setAlignment(Pos.CENTER_LEFT);
        return tabs;
    }

    private ToggleButton buildTabButton(String label) {
        ToggleButton button = new ToggleButton(label);
        button.setCursor(Cursor.HAND);
        button.setPadding(new Insets(6, 14, 6, 14));
        button.setStyle("""
            -fx-background-color: #111827;
            -fx-background-radius: 999;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 999;
            -fx-text-fill: #cbd5e1;
            -fx-font-weight: 700;
        """);
        button.selectedProperty().addListener((obs, was, is) -> {
            if (is) {
                button.setStyle("""
                    -fx-background-color: #1d4ed8;
                    -fx-background-radius: 999;
                    -fx-border-color: #1f2a44;
                    -fx-border-radius: 999;
                    -fx-text-fill: #f8fafc;
                    -fx-font-weight: 800;
                """);
            } else {
                button.setStyle("""
                    -fx-background-color: #111827;
                    -fx-background-radius: 999;
                    -fx-border-color: #1f2a44;
                    -fx-border-radius: 999;
                    -fx-text-fill: #cbd5e1;
                    -fx-font-weight: 700;
                """);
            }
        });
        return button;
    }

    private void selectTab(String name) {
        boolean isDashboard = "Dashboard".equals(name);
        if (dashboardContent != null) {
            dashboardContent.setVisible(isDashboard);
            dashboardContent.setManaged(isDashboard);
        }
        if (shooterContent != null) {
            shooterContent.setVisible(!isDashboard);
            shooterContent.setManaged(!isDashboard);
        }
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

    private void setupLogWindow() {
        log.setEditable(false);
        log.setWrapText(true);
        log.setStyle("""
            -fx-control-inner-background: #0f172a;
            -fx-text-fill: #e5e7eb;
            -fx-highlight-fill: #334155;
        """);

        Label label = new Label("Log");
        label.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: 700;");

        VBox box = new VBox(8, label, log);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: #0b1220;");

        logStage = new javafx.stage.Stage();
        logStage.setTitle("FRC Dashboard Log");
        logStage.setScene(new javafx.scene.Scene(box, 600, 240));
        logStage.show();

        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.windowProperty().addListener((obsWindow, oldWindow, newWindow) -> {
                if (newWindow == null) return;
                newWindow.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, e -> {
                    if (logStage != null) logStage.close();
                });
            });
        });
    }

    private void appendLog(String msg) {
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        log.appendText("[" + ts + "] " + msg + "\n");
    }

    private StackPane buildMatchTimerTile() {
        matchTimerLabel.setStyle("""
            -fx-text-fill: #f8fafc;
            -fx-font-weight: 900;
            -fx-font-size: 36;
        """);

        Label title = new Label("Match Timer");
        title.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700;");

        hubStatusLabel.setStyle("""
            -fx-text-fill: #f8fafc;
            -fx-font-weight: 800;
            -fx-font-size: 14;
        """);

        hubAllianceLabel.setStyle("""
            -fx-text-fill: #94a3b8;
            -fx-font-weight: 700;
            -fx-font-size: 12;
        """);

        hubCountdownLabel.setStyle("""
            -fx-text-fill: #94a3b8;
            -fx-font-weight: 900;
            -fx-font-size: 22;
        """);

        VBox content = new VBox(6, title, matchTimerLabel, hubStatusLabel, hubAllianceLabel, hubCountdownLabel);
        content.setAlignment(Pos.CENTER);

        StackPane tile = new StackPane(content);
        tile.setPadding(new Insets(10));
        tile.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 16;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 16;
        """);

        return tile;
    }

    private Button buildActionTile(String titleText, Runnable action) {
        Button tile = new Button(titleText);
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setMaxHeight(Double.MAX_VALUE);
        tile.setOnAction(e -> {
            playClickAnimation(tile);
            action.run();
        });

        tile.setPadding(new Insets(10));
        tile.setCursor(Cursor.HAND);
        tile.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 16;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 16;
            -fx-text-fill: #f8fafc;
            -fx-font-weight: 700;
            -fx-font-size: 16;
        """);

        return tile;
    }

    private void playClickAnimation(Button tile) {
        ScaleTransition down = new ScaleTransition(Duration.millis(80), tile);
        down.setToX(0.96);
        down.setToY(0.96);

        ScaleTransition up = new ScaleTransition(Duration.millis(110), tile);
        up.setToX(1.0);
        up.setToY(1.0);

        SequentialTransition seq = new SequentialTransition(down, up);
        seq.playFromStart();
    }

    private void updateMatchTimer() {
        if (matchTimeSub == null) return;
        double seconds = matchTimeSub.get();
        if (seconds < 0) seconds = 0;
        matchTimerLabel.setText(formatSecondsFloor(seconds));
    }

    private void updateHubTimer() {
        if (matchTimeSub == null || allianceSub == null) return;
        double remaining = matchTimeSub.get();
        if (remaining <= 0) return;

        double elapsed = MATCH_SECONDS - remaining;
        if (elapsed < 0) elapsed = 0;

        String alliance = allianceSub.get().trim();
        boolean isBlue = alliance.equalsIgnoreCase("Blue");
        boolean isRed = alliance.equalsIgnoreCase("Red");

        if (elapsed < AUTO_SECONDS) {
            setHubStatus("BOTH ACTIVE", "--", "--", "#94a3b8");
            return;
        }

        double teleopRemaining = remaining;
        if (teleopRemaining > TELEOP_SECONDS) teleopRemaining = TELEOP_SECONDS;
        if (teleopRemaining < 0) teleopRemaining = 0;
        double teleopElapsed = TELEOP_SECONDS - teleopRemaining;
        if (teleopElapsed < TRANSITION_SECONDS) {
            setHubStatus(
                "BOTH ACTIVE",
                "--",
                formatSecondsFloor(TRANSITION_SECONDS - teleopElapsed),
                "#94a3b8"
            );
            return;
        }

        double shiftsElapsed = teleopElapsed - TRANSITION_SECONDS;
        double shiftsTotal = SHIFT_SECONDS * SHIFT_COUNT;
        if (shiftsElapsed < shiftsTotal) {
            int shiftIndex = (int) Math.floor(shiftsElapsed / SHIFT_SECONDS);
            double shiftTimeLeft = SHIFT_SECONDS - (shiftsElapsed % SHIFT_SECONDS);

            String winner = autoWinnerSub != null ? autoWinnerSub.get().trim() : "";
            boolean winnerBlue = winner.equalsIgnoreCase("Blue");
            boolean winnerRed = winner.equalsIgnoreCase("Red");

            if (!(winnerBlue || winnerRed) || !(isBlue || isRed)) {
                setHubStatus("HUB: ?", "--", formatSecondsFloor(shiftTimeLeft), "#94a3b8");
                return;
            }

            boolean shift1ActiveBlue = winnerRed;
            boolean activeBlue = (shiftIndex % 2 == 0) ? shift1ActiveBlue : !shift1ActiveBlue;
            boolean isActive = isBlue ? activeBlue : !activeBlue;

            String status = isActive ? "HUB ACTIVE" : "HUB INACTIVE";
            String color;
            if (isActive && shiftTimeLeft <= SWITCH_WARNING_SECONDS) {
                color = "#ef4444";
            } else if (!isActive && shiftTimeLeft <= (SHIFT_SECONDS - GREEN_DELAY_SECONDS)) {
                color = "#22c55e";
            } else {
                color = "#f8fafc";
            }

            String activeTeam = activeBlue ? "BLUE ACTIVE" : "RED ACTIVE";
            setHubStatus(status, activeTeam, formatSecondsFloor(shiftTimeLeft), color);
            return;
        }

        double endgameElapsed = shiftsElapsed - shiftsTotal;
        if (endgameElapsed < ENDGAME_SECONDS) {
            setHubStatus(
                "BOTH ACTIVE",
                "BOTH ACTIVE",
                formatSecondsFloor(ENDGAME_SECONDS - endgameElapsed),
                "#94a3b8"
            );
            return;
        }

        setHubStatus("BOTH ACTIVE", "BOTH ACTIVE", "--", "#94a3b8");
    }

    private void setHubStatus(String status, String team, String timer, String color) {
        hubStatusLabel.setText(status);
        hubAllianceLabel.setText(team);
        hubCountdownLabel.setText(timer);
        hubCountdownLabel.setStyle(String.format("""
            -fx-text-fill: %s;
            -fx-font-weight: 900;
            -fx-font-size: 28;
        """, color));
    }

    private void updateShooterStatus() {
        double target = shooterTargetSub != null ? shooterTargetSub.get() : 0.0;
        double actual = shooterRpmSub != null ? shooterRpmSub.get() : 0.0;
        shooterTargetLabel.setText(String.format("Target: %.0f RPM", target));
        shooterActualLabel.setText(String.format("Actual: %.0f RPM", actual));

        boolean atSpeed = Math.abs(actual - target) <= SHOOTER_RPM_TOLERANCE && target > 0.0;
        shooterStatusLabel.setText(atSpeed ? "AT SPEED" : "NOT READY");
        shooterStatusLabel.setStyle(String.format("""
            -fx-text-fill: %s;
            -fx-font-weight: 800;
            -fx-font-size: 16;
        """, atSpeed ? "#22c55e" : "#ef4444"));
    }

    private String formatSecondsFloor(double seconds) {
        int totalSeconds = (int) Math.floor(seconds);
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private StackPane buildFieldTile() {
        poseSub = NetworkTableInstance.getDefault()
            .getStructTopic("/AdvantageKit/RealOutputs/RobotPose", Pose2d.struct)
            .subscribe(new Pose2d());
        poseArraySub = NT.subDoubleArray("/AdvantageKit/RealOutputs/", new double[] {0.0, 0.0, 0.0});
        allianceSub = NT.subString("/Dashboard/Alliance", "Blue");
        gamePieceSub = NT.subDoubleArray("/Dashboard/GamePieces", new double[0]);

        fieldFullImage = loadFieldImage("/2026Feild.png");

        fieldImageView = new ImageView();
        fieldImageView.setPreserveRatio(false);

        fieldLayer = new Pane();
        fieldLayer.setPickOnBounds(false);

        gamePieceLayer = new Pane();
        gamePieceLayer.setPickOnBounds(false);

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

        fieldLayer.getChildren().addAll(gamePieceLayer, robotMarker);

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

        if (fieldFullImage == null) {
            Label placeholder = new Label("Add 2026Feild.png to src/main/resources");
            placeholder.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12;");
            tile.getChildren().add(placeholder);
        }

        return tile;
    }

    private Button buildPulseButton(String titleText, BooleanPublisher publisher, String logMessage) {
        Button button = new Button(titleText);
        button.setOnAction(e -> {
            playClickAnimation(button);
            publisher.set(true);
            appendLog(logMessage);

            PauseTransition pt = new PauseTransition(Duration.millis(200));
            pt.setOnFinished(ev -> publisher.set(false));
            pt.play();
        });

        button.setPadding(new Insets(10, 18, 10, 18));
        button.setCursor(Cursor.HAND);
        button.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 10;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 10;
            -fx-text-fill: #f8fafc;
            -fx-font-weight: 700;
            -fx-font-size: 14;
        """);

        return button;
    }

    private Button buildSetButton(String titleText, BooleanPublisher publisher, boolean value, String logMessage) {
        Button button = new Button(titleText);
        button.setOnAction(e -> {
            playClickAnimation(button);
            publisher.set(value);
            appendLog(logMessage);
        });

        button.setPadding(new Insets(10, 18, 10, 18));
        button.setCursor(Cursor.HAND);
        button.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 10;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 10;
            -fx-text-fill: #f8fafc;
            -fx-font-weight: 700;
            -fx-font-size: 14;
        """);

        return button;
    }

    private StackPane buildShooterTile() {
        Label title = new Label("Shooter RPM");
        title.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700;");

        shooterStatusLabel.setStyle("""
            -fx-text-fill: #ef4444;
            -fx-font-weight: 800;
            -fx-font-size: 16;
        """);
        shooterTargetLabel.setStyle("-fx-text-fill: #cbd5e1;");
        shooterActualLabel.setStyle("-fx-text-fill: #cbd5e1;");

        VBox content = new VBox(6, title, shooterTargetLabel, shooterActualLabel, shooterStatusLabel);
        content.setAlignment(Pos.CENTER);

        StackPane tile = new StackPane(content);
        tile.setPadding(new Insets(10));
        tile.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 16;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 16;
        """);

        return tile;
    }

    private Button buildActionTile(String titleText, BooleanPublisher publisher, String logMessage) {
        return buildActionTile(titleText, () -> {
            publisher.set(true);
            appendLog(logMessage);

            PauseTransition pt = new PauseTransition(Duration.millis(200));
            pt.setOnFinished(ev -> publisher.set(false));
            pt.play();
        });
    }

    private StackPane buildBoostTile() {
        Label title = new Label("Velocity Boost");
        title.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700;");

        shooterBoostSlider = new Slider(0.0, 2000.0, 0.0);
        shooterBoostSlider.setShowTickMarks(true);
        shooterBoostSlider.setShowTickLabels(true);
        shooterBoostSlider.setMajorTickUnit(500.0);
        shooterBoostSlider.setMinorTickCount(4);
        shooterBoostSlider.setBlockIncrement(50.0);
        shooterBoostSlider.setPrefWidth(260);

        boostValueLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: 800;");
        shooterBoostSlider.valueProperty().addListener((obs, oldV, newV) -> {
            boostValueLabel.setText(String.format("Boost: +%.0f RPM", newV.doubleValue()));
        });

        Button boostDown = new Button("-200");
        Button boostUp = new Button("+200");
        styleBoostButton(boostDown, "#7f1d1d", "#ef4444", "#991b1b");
        styleBoostButton(boostUp, "#14532d", "#22c55e", "#166534");
        boostDown.setOnAction(e -> {
            playClickAnimation(boostDown);
            adjustBoostBy(-200.0);
        });
        boostUp.setOnAction(e -> {
            playClickAnimation(boostUp);
            adjustBoostBy(200.0);
        });

        HBox boostButtons = new HBox(10, boostDown, boostUp);
        boostButtons.setAlignment(Pos.CENTER);

        VBox content = new VBox(8, title, boostValueLabel, shooterBoostSlider, boostButtons);
        content.setAlignment(Pos.CENTER);

        StackPane tile = new StackPane(content);
        tile.setPadding(new Insets(10));
        tile.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 16;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 16;
        """);

        return tile;
    }

    private void styleBoostButton(Button button, String base, String border, String hover) {
        button.setPrefWidth(120);
        button.setPrefHeight(40);
        button.setCursor(Cursor.HAND);
        button.setPadding(new Insets(8, 20, 8, 20));
        String baseStyle = String.format("""
            -fx-background-color: %s;
            -fx-background-radius: 12;
            -fx-border-color: %s;
            -fx-border-radius: 12;
            -fx-text-fill: #f8fafc;
            -fx-font-weight: 900;
            -fx-font-size: 16;
        """, base, border);
        String hoverStyle = String.format("""
            -fx-background-color: %s;
            -fx-background-radius: 12;
            -fx-border-color: %s;
            -fx-border-radius: 12;
            -fx-text-fill: #f8fafc;
            -fx-font-weight: 900;
            -fx-font-size: 16;
        """, hover, border);
        button.setStyle(baseStyle);
        button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(baseStyle));
    }

    private void adjustBoostBy(double delta) {
        if (shooterBoostSlider == null) return;
        double min = shooterBoostSlider.getMin();
        double max = shooterBoostSlider.getMax();
        double next = shooterBoostSlider.getValue() + delta;
        shooterBoostSlider.setValue(Math.max(min, Math.min(max, next)));
    }

    private Parent buildShooterControlTab() {
        Label title = new Label("Shooter Controls");
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: 800; -fx-font-size: 18;");

        Label rpmLabel = new Label("Shooter RPM");
        rpmLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: 700;");
        TextField rpmField = new TextField();
        rpmField.setPromptText("Type RPM (ex: 3200)");
        styleInputField(rpmField);

        Label rpmSliderLabel = new Label("RPM Slider");
        rpmSliderLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700;");
        Slider rpmSlider = new Slider(0.0, 6000.0, 0.0);
        rpmSlider.setShowTickMarks(true);
        rpmSlider.setShowTickLabels(true);
        rpmSlider.setMajorTickUnit(1000.0);
        rpmSlider.setMinorTickCount(4);
        rpmSlider.setBlockIncrement(50.0);
        rpmSlider.setPrefWidth(260);
        Label rpmSliderValue = new Label("Slider: 0 RPM");
        rpmSliderValue.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 600;");

        Label hoodLabel = new Label("Hood Setpoint (deg)");
        hoodLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: 700;");
        TextField hoodField = new TextField();
        hoodField.setPromptText("Type angle (ex: 25)");
        styleInputField(hoodField);

        Label hoodSliderLabel = new Label("Hood Slider");
        hoodSliderLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700;");
        Slider hoodSlider = new Slider(0.0, 90.0, 0.0);
        hoodSlider.setShowTickMarks(true);
        hoodSlider.setShowTickLabels(true);
        hoodSlider.setMajorTickUnit(15.0);
        hoodSlider.setMinorTickCount(2);
        hoodSlider.setBlockIncrement(1.0);
        hoodSlider.setPrefWidth(260);
        Label hoodSliderValue = new Label("Slider: 0 deg");
        hoodSliderValue.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 600;");

        Label rpmStatus = new Label("Last sent: -- RPM");
        rpmStatus.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 600;");
        Label hoodStatus = new Label("Last sent: -- deg");
        hoodStatus.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 600;");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: 700;");

        Button sendButton = new Button("Send Setpoints");
        sendButton.setOnAction(e -> {
            String rpmText = rpmField.getText();
            boolean hasRpm = rpmText != null && !rpmText.isBlank();
            double rpm = Double.NaN;

            if (hasRpm) {
                rpm = parseDoubleOrWarn(rpmText, "RPM", errorLabel);
                if (Double.isNaN(rpm)) return;
            }

            double hoodDeg = parseDoubleOrWarn(hoodField.getText(), "Hood angle", errorLabel);
            if (Double.isNaN(hoodDeg)) return;

            if (hasRpm) {
                double boostRpm = shooterBoostSlider != null ? shooterBoostSlider.getValue() : 0.0;
                double boostedRpm = rpm + boostRpm;
                shooterTargetCmd.set(boostedRpm);
                rpmStatus.setText(String.format(
                    "Last sent: %.0f RPM (boost +%.0f RPM -> %.0f RPM)",
                    rpm,
                    boostRpm,
                    boostedRpm
                ));
                appendLog(String.format(
                    "Setpoints sent: %.0f RPM (boost +%.0f RPM -> %.0f RPM), hood %.1f deg",
                    rpm,
                    boostRpm,
                    boostedRpm,
                    hoodDeg
                ));
            } else {
                appendLog(String.format("Hood setpoint sent: %.1f deg", hoodDeg));
            }

            hoodSetpointCmd.set(hoodDeg);
            hoodStatus.setText(String.format("Last sent: %.1f deg", hoodDeg));
            errorLabel.setText("");
        });
        sendButton.setPadding(new Insets(10, 18, 10, 18));
        sendButton.setCursor(Cursor.HAND);
        sendButton.setStyle("""
            -fx-background-color: #1d4ed8;
            -fx-background-radius: 10;
            -fx-text-fill: #f8fafc;
            -fx-font-weight: 800;
            -fx-font-size: 14;
        """);

        boolean[] updatingRpm = {false};
        rpmSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (updatingRpm[0]) return;
            updatingRpm[0] = true;
            double value = newV.doubleValue();
            rpmField.setText(String.format("%.0f", value));
            rpmSliderValue.setText(String.format("Slider: %.0f RPM", value));
            updatingRpm[0] = false;
        });
        rpmField.textProperty().addListener((obs, oldV, newV) -> {
            if (updatingRpm[0]) return;
            if (newV == null || newV.isBlank()) {
                rpmSliderValue.setText("Slider: 0 RPM");
                return;
            }
            try {
                double value = Double.parseDouble(newV.trim());
                double clamped = Math.max(rpmSlider.getMin(), Math.min(rpmSlider.getMax(), value));
                updatingRpm[0] = true;
                rpmSlider.setValue(clamped);
                rpmSliderValue.setText(String.format("Slider: %.0f RPM", clamped));
                updatingRpm[0] = false;
            } catch (NumberFormatException ignored) {
            }
        });

        boolean[] updatingHood = {false};
        hoodSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (updatingHood[0]) return;
            updatingHood[0] = true;
            double value = newV.doubleValue();
            hoodField.setText(String.format("%.1f", value));
            hoodSliderValue.setText(String.format("Slider: %.1f deg", value));
            updatingHood[0] = false;
        });
        hoodField.textProperty().addListener((obs, oldV, newV) -> {
            if (updatingHood[0]) return;
            if (newV == null || newV.isBlank()) {
                hoodSliderValue.setText("Slider: 0 deg");
                return;
            }
            try {
                double value = Double.parseDouble(newV.trim());
                double clamped = Math.max(hoodSlider.getMin(), Math.min(hoodSlider.getMax(), value));
                updatingHood[0] = true;
                hoodSlider.setValue(clamped);
                hoodSliderValue.setText(String.format("Slider: %.1f deg", clamped));
                updatingHood[0] = false;
            } catch (NumberFormatException ignored) {
            }
        });

        rpmField.setOnAction(e -> sendButton.fire());
        hoodField.setOnAction(e -> sendButton.fire());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(140);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);
        grid.add(rpmLabel, 0, 0);
        grid.add(rpmField, 1, 0);
        grid.add(rpmSliderLabel, 0, 1);
        grid.add(new VBox(4, rpmSlider, rpmSliderValue), 1, 1);
        grid.add(hoodLabel, 0, 2);
        grid.add(hoodField, 1, 2);
        grid.add(hoodSliderLabel, 0, 3);
        grid.add(new VBox(4, hoodSlider, hoodSliderValue), 1, 3);

        Button spindexerStartButton = new Button("Start Spindexer");
        spindexerStartButton.setOnAction(e -> {
            playClickAnimation(spindexerStartButton);
            spindexerReverseCmd.set(false);
            spindexerCmd.set(true);
            appendLog("Spindexer set true");
        });
        styleActionButton(spindexerStartButton);

        Button spindexerReverseButton = new Button("Reverse Spindexer");
        spindexerReverseButton.setOnAction(e -> {
            playClickAnimation(spindexerReverseButton);
            spindexerCmd.set(false);
            spindexerReverseCmd.set(true);
            appendLog("Spindexer reverse set true");
        });
        styleActionButton(spindexerReverseButton);

        Button spindexerStopButton = new Button("Stop Spindexer");
        spindexerStopButton.setOnAction(e -> {
            playClickAnimation(spindexerStopButton);
            spindexerCmd.set(false);
            spindexerReverseCmd.set(false);
            appendLog("Spindexer set false");
        });
        styleActionButton(spindexerStopButton);

        HBox spindexerRow = new HBox(10, spindexerStartButton, spindexerReverseButton, spindexerStopButton);

        VBox card = new VBox(12, title, grid, sendButton, errorLabel, rpmStatus, hoodStatus, spindexerRow);
        card.setPadding(new Insets(18));
        card.setMaxWidth(520);
        card.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 16;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 16;
        """);

        StackPane wrapper = new StackPane(card);
        StackPane.setMargin(card, new Insets(20));
        return wrapper;
    }

    private void styleInputField(TextField field) {
        field.setPrefWidth(260);
        field.setStyle("""
            -fx-background-color: #111827;
            -fx-background-radius: 8;
            -fx-border-color: #38bdf8;
            -fx-border-radius: 8;
            -fx-border-width: 2;
            -fx-text-fill: #f8fafc;
            -fx-prompt-text-fill: #94a3b8;
            -fx-font-size: 14;
            -fx-font-weight: 700;
        """);
        field.focusedProperty().addListener((obs, was, is) -> {
            if (is) {
                field.setStyle("""
                    -fx-background-color: #0b1220;
                    -fx-background-radius: 8;
                    -fx-border-color: #22c55e;
                    -fx-border-radius: 8;
                    -fx-border-width: 2;
                    -fx-text-fill: #f8fafc;
                    -fx-prompt-text-fill: #94a3b8;
                    -fx-font-size: 14;
                    -fx-font-weight: 700;
                """);
            } else {
                field.setStyle("""
                    -fx-background-color: #111827;
                    -fx-background-radius: 8;
                    -fx-border-color: #38bdf8;
                    -fx-border-radius: 8;
                    -fx-border-width: 2;
                    -fx-text-fill: #f8fafc;
                    -fx-prompt-text-fill: #94a3b8;
                    -fx-font-size: 14;
                    -fx-font-weight: 700;
                """);
            }
        });
    }

    private void styleActionButton(Button button) {
        button.setPadding(new Insets(10, 18, 10, 18));
        button.setCursor(Cursor.HAND);
        button.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 10;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 10;
            -fx-text-fill: #f8fafc;
            -fx-font-weight: 700;
            -fx-font-size: 14;
        """);
    }

    private double parseDoubleOrWarn(String text, String label, Label errorLabel) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            errorLabel.setText(label + " is required.");
            return Double.NaN;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ex) {
            errorLabel.setText(label + " must be a number.");
            return Double.NaN;
        }
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

        if (fieldFullImage != null && fieldImageView.getImage() != fieldFullImage) {
            fieldImageView.setImage(fieldFullImage);
        }

        if (!alliance.equalsIgnoreCase(lastAlliance)) {
            lastAlliance = alliance;
            fieldImageView.setViewport(null);
        }

        double fieldW = fieldImageView.getFitWidth();
        double fieldH = fieldImageView.getFitHeight();
        if (fieldW <= 0 || fieldH <= 0) return;
        if (fieldW != lastFieldW || fieldH != lastFieldH) {
            lastFieldW = fieldW;
            lastFieldH = fieldH;
            appendLog(String.format("Field image size: %.0f x %.0f px", fieldW, fieldH));
        }

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

        double nx = (poseX - FIELD_X_MIN_METERS) / (FIELD_X_MAX_METERS - FIELD_X_MIN_METERS);
        double ny = (poseY - FIELD_Y_MIN_METERS) / (FIELD_Y_MAX_METERS - FIELD_Y_MIN_METERS);
        if (INVERT_X) nx = 1.0 - nx;
        if (INVERT_Y) ny = 1.0 - ny;

        if (INVERT_X) poseDeg = 180.0 - poseDeg;
        if (INVERT_Y) poseDeg = -poseDeg;

        poseDeg = ((poseDeg % 360.0) + 360.0) % 360.0;

        double xPx = nx * fieldW;
        double yPx = ny * fieldH;

        double metersPerPixelX = (FIELD_X_MAX_METERS - FIELD_X_MIN_METERS) / fieldW;
        double metersPerPixelY = (FIELD_Y_MAX_METERS - FIELD_Y_MIN_METERS) / fieldH;
        double pixelsPerMeter = 1.0 / Math.max(metersPerPixelX, metersPerPixelY);
        double size = ROBOT_SIZE_METERS * pixelsPerMeter;
        size = Math.max(16.0, Math.min(size, Math.min(fieldW, fieldH)));
        robotMarker.setPrefSize(size, size);
        robotMarker.setLayoutX(xPx - size / 2.0);
        robotMarker.setLayoutY(yPx - size / 2.0);
        robotMarker.setRotate(poseDeg);

        updateGamePieces(fieldW, fieldH, pixelsPerMeter);
    }

    private void updateGamePieces(double fieldW, double fieldH, double pixelsPerMeter) {
        if (gamePieceLayer == null || gamePieceSub == null) return;
        double[] arr = gamePieceSub.get();
        int count = arr.length / 2;

        while (gamePieceMarkers.size() < count) {
            Region r = new Region();
            r.setStyle("""
                -fx-background-color: #fbbf24;
                -fx-border-color: #0f172a;
                -fx-border-width: 2;
                -fx-background-radius: 999;
                -fx-border-radius: 999;
            """);
            gamePieceMarkers.add(r);
            gamePieceLayer.getChildren().add(r);
        }

        double size = GAME_PIECE_DIAMETER_METERS * pixelsPerMeter;
        size = Math.max(6.0, Math.min(size, Math.min(fieldW, fieldH)));

        for (int i = 0; i < gamePieceMarkers.size(); i++) {
            Region r = gamePieceMarkers.get(i);
            if (i >= count) {
                r.setVisible(false);
                continue;
            }
            double x = arr[i * 2];
            double y = arr[i * 2 + 1];

            double nx = (x - FIELD_X_MIN_METERS) / (FIELD_X_MAX_METERS - FIELD_X_MIN_METERS);
            double ny = (y - FIELD_Y_MIN_METERS) / (FIELD_Y_MAX_METERS - FIELD_Y_MIN_METERS);
            if (INVERT_X) nx = 1.0 - nx;
            if (INVERT_Y) ny = 1.0 - ny;

            double xPx = nx * fieldW;
            double yPx = ny * fieldH;

            r.setVisible(true);
            r.setPrefSize(size, size);
            r.setLayoutX(xPx - size / 2.0);
            r.setLayoutY(yPx - size / 2.0);
        }
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
