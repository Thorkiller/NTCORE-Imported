package frc.robot.frcdash;


import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.DoubleArraySubscriber;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringArrayPublisher;
import edu.wpi.first.networktables.StringSubscriber;
import edu.wpi.first.networktables.StructSubscriber;
import edu.wpi.first.networktables.TimestampedObject;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.util.WPIUtilJNI;
import frc.robot.frcdash.ui.BooleanTopicTile;
import frc.robot.frcdash.ui.DoubleTopicTile;
import frc.robot.frcdash.ui.StringTopicTile;
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
import javafx.stage.FileChooser;

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
import java.util.Arrays;

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
    private static final double MOTOR_TOO_HOT_THRESHOLD_C = 54.4444444444;
    private static final long MOTOR_HEALTH_STALE_MICROS = 1_500_000L;

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
    private final DoublePublisher shooterTargetCmd = NT.pubDouble("/Dashboard/ShooterTargetRPM");
    private final DoublePublisher shooterBoostCmd = NT.pubDouble("/Dashboard/ShooterBoostRPM");
    private final BooleanPublisher hoodAngleCmd = NT.pubBool("/Dashboard/HoodAngleDeg");
    private final DoublePublisher hoodSetpointCmd = NT.pubDouble("/Dashboard/HoodSetpointDeg");
    private final BooleanPublisher spindexerCmd = NT.pubBool("/Dashboard/Spindexer");
    private final BooleanPublisher spindexerReverseCmd = NT.pubBool("/Dashboard/SpindexerReverse");
    private final BooleanPublisher funnlingCmd = NT.pubBool("/Dashboard/Funnling");
    private final BooleanPublisher manualCmd = NT.pubBool("/Dashboard/Manual");
    private final BooleanPublisher confirmCmd = NT.pubBool("/Dashboard/Confirm");
    private final BooleanPublisher passingHumanPlayerCmd = NT.pubBool("/1771 passing/Human Player");
    private final BooleanPublisher passingDepotCmd = NT.pubBool("/1771 passing/Depot");

    // Editable tiles (robot -> dashboard)
    private DoubleTopicTile batteryTile;
    private DoubleTopicTile intakeMotorTempTile;
    private DoubleTopicTile intakeWheelsTempTile;
    private StringTopicTile enabledTile;
    private BooleanTopicTile visionTile;
    private StackPane fieldTile;
    private StackPane matchTimerTile;
    private Button zeroGyroTile;
    private Button driveModeTile;
    private StackPane motorHealthTile;
    private StackPane roboRioHealthTile;
    private Button shootTile;
    private Button intakeTile;
    private Button intakeInTile;
    private Button spindexerReverseTile;
    private Button hoodDownTile;
    private Button confirmTile;
    private ToggleButton funnlingTile;
    private ToggleButton manualTile;
    private ToggleButton passingHumanPlayerTile;
    private ToggleButton passingDepotTile;
    private StackPane passingTile;
    private StackPane shooterTile;
    private StackPane boostTile;
    private Slider shooterBoostSlider;
    private TextField shooterRpmField;
    private Slider shooterRpmSlider;
    private Label shooterRpmSliderValueLabel;
    private TextField hoodSetpointField;
    private Slider hoodSetpointSlider;
    private Label hoodSliderValueLabel;
    private Label shooterRpmStatusLabel;
    private Label hoodSetpointStatusLabel;
    private Label shooterControlErrorLabel;
    private final Label boostValueLabel = new Label("Boost: +0 RPM");
    private final Label shooterStatusLabel = new Label("NOT READY");
    private final Label shooterActualLabel = new Label("Actual: -- RPM");
    private final Label shooterTargetLabel = new Label("Target: -- RPM");
    private final Label matchTimerLabel = new Label("02:15");
    private final Label canShootLabel = new Label("CAN SHOOT: --");
    private final Label hubStatusLabel = new Label("HUB: --");
    private final Label hubCountdownLabel = new Label("--");
    private final Label hubAllianceLabel = new Label("--");
    private final Label motorHealthSummaryLabel = new Label("Motor health: --");
    private final Label motorDisconnectedLabel = new Label("Disconnected: --");
    private final Label motorHotLabel = new Label("Hot: --");
    private final Label roboRioHealthSummaryLabel = new Label("roboRIO health: --");
    private final Label roboRioRestartLabel = new Label("Restart: --");
    private final Label roboRioPowerLabel = new Label("Power: --");
    private final Label roboRioRailsLabel = new Label("Rails: --");
    private final Label replayStatusLabel = new Label("Replay idle");
    private final List<String> latestMotorHealthIssues = new ArrayList<>();
    private final List<String> latestRoboRioIssues = new ArrayList<>();
    private javafx.stage.Stage logStage;
    private Button recordReplayButton;
    private Button stopRecordReplayButton;
    private Button loadReplayButton;
    private Button playReplayButton;
    private Button stopReplayButton;
    private Slider replayScrubber;
    private Label replayTimeLabel;
    private Button shooterBoostDownButton;
    private Button shooterBoostUpButton;
    private Button shooterSendSetpointsButton;
    private Button spindexerStartButton;
    private Button spindexerControlReverseButton;
    private Button spindexerStopButton;
    private boolean ntConnected;
    private boolean suppressDashboardPublishing;
    private boolean suppressReplayScrubberEvents;
    private final List<String> pendingReplayEvents = new ArrayList<>();

    private final DoubleSubscriber matchTimeSub = NT.subDouble("/AdvantageKit/RealOutputs/MatchTime", 0.0);
    private final StringSubscriber autoWinnerSub = NT.subString("/AdvantageKit/RealOutputs/AutoWinner", "");
    private final DoubleSubscriber shooterRpmSub = NT.subDouble("/AdvantageKit/Shooter/ShooterRPM", 0.0);
    private final DoubleSubscriber shooterTargetSub = NT.subDouble("/AdvantageKit/Shooter/TargetRPM", 0.0);
    private final NetworkTable motorHealthTable = NetworkTableInstance.getDefault().getTable("MotorHealth");
    private final NetworkTable roboRioHealthTable = NetworkTableInstance.getDefault().getTable("RoboRIOHealth");
    private final BooleanPublisher allConnectedPub = NT.pubBool("/MotorHealth/AllConnected");
    private final BooleanPublisher allCoolPub = NT.pubBool("/MotorHealth/AllCool");
    private final BooleanPublisher healthyPub = NT.pubBool("/MotorHealth/Healthy");
    private final StringArrayPublisher disconnectedMotorsPub = NT.pubStringArray("/MotorHealth/DisconnectedMotors");
    private final StringArrayPublisher hotMotorsPub = NT.pubStringArray("/MotorHealth/HotMotors");
    private final DoublePublisher tooHotThresholdPub = NT.pubDouble("/MotorHealth/TooHotThresholdC");
    private final edu.wpi.first.networktables.StringPublisher overallStatusPub = NT.pubString("/MotorHealth/OverallStatus");
    private final BooleanPublisher roboRioHealthyPub = NT.pubBool("/RoboRIOHealth/Healthy");
    private final edu.wpi.first.networktables.StringPublisher roboRioStatusPub = NT.pubString("/RoboRIOHealth/Status");
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
    private final DashboardReplayManager replayManager = new DashboardReplayManager(
        Paths.get(System.getProperty("user.home"), ".frcdash", "replays"),
        this::captureSnapshot,
        this::applySnapshot,
        this::appendLog
    );

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
            ntConnected = connected;
            setConnState(connected);
            appendLog(connected ? "Connected" : "Disconnected");
        });

        new AnimationTimer() {
            @Override public void handle(long now) {
                refreshReplayTimelineUi();
                if (replayManager.isReplaying()) {
                    return;
                }
                if (batteryTile != null) batteryTile.update();
                if (intakeMotorTempTile != null) intakeMotorTempTile.update();
                if (intakeWheelsTempTile != null) intakeWheelsTempTile.update();
                if (enabledTile != null) enabledTile.update();
                if (visionTile != null) visionTile.update();
                if (fieldTile != null) updateFieldPose();
                updateMatchTimer();
                updateHubTimer();
                updateShooterStatus();
                updateMotorHealth();
                updateRoboRioHealth();
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
            "/AdvantageKit/SystemStats/BatteryVoltage",
            0.0,
            v -> String.format("%.2f V", v)
        );
        intakeMotorTempTile = new DoubleTopicTile(
            inst,
            "Intake Motor Temp",
            "Topic: /AdvantageKit/Intake/IntakeMotorTemp",
            "/AdvantageKit/Intake/IntakeMotorTemp",
            0.0,
            v -> String.format("%.1f C", v)
        );
        intakeWheelsTempTile = new DoubleTopicTile(
            inst,
            "Intake Wheels Temp",
            "Topic: /AdvantageKit/Intake/IntakeWheelTemp",
            "/AdvantageKit/Intake/IntakeWheelTemp",
            0.0,
            v -> String.format("%.1f C", v)
        );
        enabledTile = new StringTopicTile(
            inst,
            "Robot State",
            "Topic: /Dashboard/Enabled",
            "/AdvantageKit/Superstructure/CurrentState",
            "",
            v -> v == null || v.isBlank() ? "--" : v
        );
        visionTile = new BooleanTopicTile(
            inst,
            "Vision",
            "Topic: /Vision/HasTarget",
            "/Vision/HasTarget",
            false,
            v -> v ? "Target is seen" : "Target is not seen"
        );

        motorHealthTile = buildMotorHealthTile();
        roboRioHealthTile = buildRoboRioHealthTile();
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
        confirmTile = buildActionTile(
            "Confirm",
            confirmCmd,
            "Confirm pressed (200ms pulse)"
        );
        intakeInTile = buildActionTile("Intake In", () -> {
            intakeInCmd.set(true);
            appendLog("IntakeIn pressed (200ms pulse)");

            PauseTransition pt = new PauseTransition(Duration.millis(200));
            pt.setOnFinished(ev -> intakeInCmd.set(false));
            pt.play();
        });
        funnlingTile = buildToggleActionTile(
            "Funnling",
            funnlingCmd,
            "Funnling toggled"
        );
        manualTile = buildToggleActionTile(
            "Manual",
            manualCmd,
            "Manual toggled"
        );
        passingTile = buildPassingTile();

        loadLayout();

        Pane pane = new Pane();
        pane.setMinHeight(1440);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(pane.widthProperty());
        clip.heightProperty().bind(pane.heightProperty());
        pane.setClip(clip);
        pane.getChildren().addAll(
            batteryTile, intakeMotorTempTile, intakeWheelsTempTile, enabledTile, visionTile, motorHealthTile, roboRioHealthTile, fieldTile, matchTimerTile,
            zeroGyroTile, driveModeTile, shootTile, intakeTile, shooterTile, boostTile,
            spindexerReverseTile, hoodDownTile, confirmTile, intakeInTile, funnlingTile, manualTile,
            passingTile);

        enableDragAndResize(batteryTile, "battery", 0, 0, 380, 160);
        enableDragAndResize(intakeMotorTempTile, "intakeMotorTemp", 420, 0, 240, 160);
        enableDragAndResize(intakeWheelsTempTile, "intakeWheelsTemp", 680, 0, 240, 160);
        enableDragAndResize(enabledTile, "enabled", 0, 190, 380, 160);
        enableDragAndResize(visionTile, "vision", 0, 360, 380, 120);
        enableDragAndResize(motorHealthTile, "motorHealth", 0, 500, 380, 210);
        enableDragAndResize(roboRioHealthTile, "roboRioHealth", 0, 730, 380, 230);
        enableDragAndResize(fieldTile, "field", 420, 190, 520, 300);
        enableDragAndResize(matchTimerTile, "matchTimer", 420, 500, 240, 120);
        enableDragAndResize(zeroGyroTile, "zeroGyro", 680, 500, 240, 120);
        enableDragAndResize(driveModeTile, "driveMode", 940, 500, 240, 120);
        enableDragAndResize(shootTile, "shoot", 420, 640, 240, 120);
        enableDragAndResize(intakeTile, "intake", 680, 640, 240, 120);
        enableDragAndResize(shooterTile, "shooter", 420, 780, 520, 160);
        enableDragAndResize(boostTile, "boost", 560, 780, 340, 160);
        enableDragAndResize(spindexerReverseTile, "spindexerReverse", 420, 960, 240, 120);
        enableDragAndResize(hoodDownTile, "hoodDown", 680, 960, 240, 120);
        enableDragAndResize(confirmTile, "confirm", 940, 960, 240, 120);
        enableDragAndResize(intakeInTile, "intakeIn", 420, 1100, 240, 120);
        enableDragAndResize(funnlingTile, "funnling", 940, 640, 240, 120);
        enableDragAndResize(manualTile, "manual", 940, 780, 240, 120);
        enableDragAndResize(passingTile, "passing1771", 940, 1100, 280, 280);

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

        HBox replayControls = buildReplayControls();
        HBox replayScrubberRow = buildReplayScrubberRow();

        VBox box = new VBox(8, label, replayControls, replayStatusLabel, replayScrubberRow, log);
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

    private HBox buildReplayControls() {
        recordReplayButton = new Button("Start Recording");
        stopRecordReplayButton = new Button("Stop Recording");
        loadReplayButton = new Button("Load Replay");
        playReplayButton = new Button("Play Replay");
        stopReplayButton = new Button("Stop Replay");

        styleReplayButton(recordReplayButton);
        styleReplayButton(stopRecordReplayButton);
        styleReplayButton(loadReplayButton);
        styleReplayButton(playReplayButton);
        styleReplayButton(stopReplayButton);

        replayStatusLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12; -fx-font-weight: 700;");

        recordReplayButton.setOnAction(e -> startReplayRecording());
        stopRecordReplayButton.setOnAction(e -> stopReplayRecording());
        loadReplayButton.setOnAction(e -> chooseReplayFile());
        playReplayButton.setOnAction(e -> toggleReplayPlayback());
        stopReplayButton.setOnAction(e -> stopReplayPlayback());

        updateReplayControls();

        HBox row = new HBox(8,
            recordReplayButton,
            stopRecordReplayButton,
            loadReplayButton,
            playReplayButton,
            stopReplayButton
        );
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox buildReplayScrubberRow() {
        replayScrubber = new Slider(0.0, 1.0, 0.0);
        replayScrubber.setPrefWidth(360);
        replayScrubber.setDisable(true);
        replayTimeLabel = new Label("00:00 / 00:00");
        replayTimeLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12; -fx-font-weight: 700;");

        replayScrubber.valueProperty().addListener((obs, oldV, newV) -> {
            if (suppressReplayScrubberEvents || replayScrubber == null || replayScrubber.isDisabled()) {
                return;
            }
            long seekMillis = Math.round(newV.doubleValue());
            replayManager.seekToElapsed(seekMillis);
            refreshReplayTimelineUi();
        });

        HBox row = new HBox(10, replayScrubber, replayTimeLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(replayScrubber, Priority.ALWAYS);
        return row;
    }

    private void styleReplayButton(Button button) {
        button.setCursor(Cursor.HAND);
        button.setStyle("""
            -fx-background-color: #111827;
            -fx-background-radius: 10;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 10;
            -fx-text-fill: #f8fafc;
            -fx-font-weight: 700;
        """);
    }

    private void appendLog(String msg) {
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        log.appendText("[" + ts + "] " + msg + "\n");
    }

    private void startReplayRecording() {
        try {
            replayManager.stopReplay();
            replayManager.startRecording();
            updateReplayControls();
            Path path = replayManager.getCurrentRecordingPath();
            replayStatusLabel.setText("Recording: " + (path != null ? path.getFileName() : ""));
            appendLog("Replay recording started");
        } catch (IOException ex) {
            replayStatusLabel.setText("Recording failed");
            appendLog("Replay recording failed: " + ex.getMessage());
        }
    }

    private void stopReplayRecording() {
        Path path = replayManager.getCurrentRecordingPath();
        replayManager.stopRecording();
        updateReplayControls();
        if (path != null) {
            replayStatusLabel.setText("Saved replay: " + path.getFileName());
            appendLog("Replay recording saved: " + path);
        } else {
            replayStatusLabel.setText("Replay idle");
        }
    }

    private void chooseReplayFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Dashboard Replay");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Dashboard Replay", "*.dashreplay")
        );
        Path latest = null;
        try {
            latest = replayManager.getLatestReplayPath();
        } catch (IOException ignored) {
        }
        if (latest != null && Files.exists(latest.getParent())) {
            chooser.setInitialDirectory(latest.getParent().toFile());
            chooser.setInitialFileName(latest.getFileName().toString());
        }
        if (logStage == null || logStage.getScene() == null) {
            return;
        }
        var file = chooser.showOpenDialog(logStage);
        if (file == null) {
            return;
        }
        loadReplay(file.toPath());
    }

    private void toggleReplayPlayback() {
        if (replayManager.isReplaying()) {
            replayManager.pauseReplay();
            replayStatusLabel.setText("Replay paused");
            appendLog("Replay paused");
        } else if (replayManager.getLoadedReplayPath() != null) {
            replayManager.playReplay();
            replayStatusLabel.setText("Replaying: " + replayManager.getLoadedReplayPath().getFileName());
            appendLog("Replay started");
        } else {
            try {
                Path latest = replayManager.getLatestReplayPath();
                if (latest == null) {
                    replayStatusLabel.setText("No replay file found");
                    return;
                }
                loadReplay(latest);
                replayManager.playReplay();
                replayStatusLabel.setText("Replaying: " + latest.getFileName());
                appendLog("Replay started");
            } catch (IOException ex) {
                replayStatusLabel.setText("Replay load failed");
                appendLog("Replay load failed: " + ex.getMessage());
            }
        }
        updateReplayControls();
    }

    private void stopReplayPlayback() {
        replayManager.stopReplay();
        updateReplayControls();
        if (replayManager.getLoadedReplayPath() != null) {
            replayStatusLabel.setText("Replay stopped: " + replayManager.getLoadedReplayPath().getFileName());
        } else {
            replayStatusLabel.setText("Replay idle");
        }
        appendLog("Replay stopped");
    }

    private void loadReplay(Path path) {
        try {
            replayManager.loadReplay(path);
            replayStatusLabel.setText(
                String.format("Loaded replay: %s (%d snapshots)",
                    path.getFileName(),
                    replayManager.getLoadedSnapshotCount())
            );
            appendLog("Replay loaded: " + path);
            updateReplayControls();
        } catch (IOException ex) {
            replayStatusLabel.setText("Replay load failed");
            appendLog("Replay load failed: " + ex.getMessage());
        }
    }

    private void updateReplayControls() {
        boolean recording = replayManager.isRecording();
        boolean replaying = replayManager.isReplaying();
        boolean hasReplay = replayManager.getLoadedReplayPath() != null;

        if (recordReplayButton != null) recordReplayButton.setDisable(recording);
        if (stopRecordReplayButton != null) stopRecordReplayButton.setDisable(!recording);
        if (loadReplayButton != null) loadReplayButton.setDisable(recording);
        if (playReplayButton != null) {
            playReplayButton.setDisable(recording);
            playReplayButton.setText(replaying ? "Pause Replay" : "Play Replay");
        }
        if (stopReplayButton != null) stopReplayButton.setDisable(!hasReplay && !replaying);
        if (replayScrubber != null) {
            replayScrubber.setDisable(recording || !hasReplay);
        }
        refreshReplayTimelineUi();
    }

    private void refreshReplayTimelineUi() {
        if (replayScrubber == null || replayTimeLabel == null) {
            return;
        }
        long total = replayManager.getReplayDurationMillis();
        long current = replayManager.getCurrentReplayElapsedMillis();
        suppressReplayScrubberEvents = true;
        replayScrubber.setMax(Math.max(1L, total));
        replayScrubber.setValue(Math.min(current, Math.max(1L, total)));
        suppressReplayScrubberEvents = false;
        replayTimeLabel.setText(
            formatReplayTime(current) + " / " + formatReplayTime(total)
        );
    }

    private String formatReplayTime(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long mins = totalSeconds / 60L;
        long secs = totalSeconds % 60L;
        return String.format("%02d:%02d", mins, secs);
    }

    private DashboardReplayManager.DashboardSnapshot captureSnapshot() {
        double[] pose = readCurrentPose();
        double[] gamePieces = gamePieceSub != null ? gamePieceSub.get() : new double[0];
        String replayEvents = drainReplayEvents();
        return new DashboardReplayManager.DashboardSnapshot(
            0L,
            ntConnected,
            dashboardTabButton != null && dashboardTabButton.isSelected() ? "Dashboard" : "Shooter",
            batteryTile != null ? batteryTile.getTopicPath() : "",
            batteryTile != null ? batteryTile.getCurrentValue() : 0.0,
            intakeMotorTempTile != null ? intakeMotorTempTile.getTopicPath() : "",
            intakeMotorTempTile != null ? intakeMotorTempTile.getCurrentValue() : 0.0,
            intakeWheelsTempTile != null ? intakeWheelsTempTile.getTopicPath() : "",
            intakeWheelsTempTile != null ? intakeWheelsTempTile.getCurrentValue() : 0.0,
            enabledTile != null ? enabledTile.getTopicPath() : "",
            enabledTile != null ? enabledTile.getCurrentValue() : "",
            visionTile != null ? visionTile.getTopicPath() : "",
            visionTile != null && visionTile.getCurrentValue(),
            matchTimeSub != null ? matchTimeSub.get() : 0.0,
            matchTimerLabel.getText(),
            canShootLabel.getText(),
            hubStatusLabel.getText(),
            hubAllianceLabel.getText(),
            hubCountdownLabel.getText(),
            shooterTargetSub != null ? shooterTargetSub.get() : 0.0,
            shooterRpmSub != null ? shooterRpmSub.get() : 0.0,
            shooterStatusLabel.getText(),
            shooterBoostSlider != null ? shooterBoostSlider.getValue() : 0.0,
            shooterRpmField != null ? shooterRpmField.getText() : "",
            shooterRpmSlider != null ? shooterRpmSlider.getValue() : 0.0,
            shooterRpmSliderValueLabel != null ? shooterRpmSliderValueLabel.getText() : "",
            hoodSetpointField != null ? hoodSetpointField.getText() : "",
            hoodSetpointSlider != null ? hoodSetpointSlider.getValue() : 0.0,
            hoodSliderValueLabel != null ? hoodSliderValueLabel.getText() : "",
            shooterRpmStatusLabel != null ? shooterRpmStatusLabel.getText() : "",
            hoodSetpointStatusLabel != null ? hoodSetpointStatusLabel.getText() : "",
            shooterControlErrorLabel != null ? shooterControlErrorLabel.getText() : "",
            replayEvents,
            funnlingTile != null && funnlingTile.isSelected(),
            manualTile != null && manualTile.isSelected(),
            passingHumanPlayerTile != null && passingHumanPlayerTile.isSelected(),
            passingDepotTile != null && passingDepotTile.isSelected(),
            allianceSub != null ? allianceSub.get().trim() : "",
            pose[0],
            pose[1],
            pose[2],
            Arrays.copyOf(gamePieces, gamePieces.length)
        );
    }

    private void applySnapshot(DashboardReplayManager.DashboardSnapshot snapshot) {
        suppressDashboardPublishing = true;
        try {
            ntConnected = snapshot.connected();
            setConnState(snapshot.connected());

            if ("Shooter".equals(snapshot.selectedTab())) {
                if (shooterTabButton != null) shooterTabButton.setSelected(true);
                selectTab("Shooter");
            } else {
                if (dashboardTabButton != null) dashboardTabButton.setSelected(true);
                selectTab("Dashboard");
            }

            if (batteryTile != null) batteryTile.showValue(snapshot.batteryValue());
            if (intakeMotorTempTile != null) intakeMotorTempTile.showValue(snapshot.intakeMotorTempValue());
            if (intakeWheelsTempTile != null) intakeWheelsTempTile.showValue(snapshot.intakeWheelsTempValue());
            if (enabledTile != null) enabledTile.showValue(snapshot.enabledValue());
            if (visionTile != null) visionTile.showValue(snapshot.visionValue());

            matchTimerLabel.setText(snapshot.matchTimerText());
            applyCanShootText(snapshot.canShootText());
            hubStatusLabel.setText(snapshot.hubStatus());
            hubAllianceLabel.setText(snapshot.hubAlliance());
            hubCountdownLabel.setText(snapshot.hubCountdown());

            shooterTargetLabel.setText(String.format("Target: %.0f RPM", snapshot.shooterTarget()));
            shooterActualLabel.setText(String.format("Actual: %.0f RPM", snapshot.shooterActual()));
            applyShooterStatusText(snapshot.shooterStatus());

            if (shooterBoostSlider != null) {
                shooterBoostSlider.setValue(snapshot.shooterBoost());
            } else {
                boostValueLabel.setText(String.format("Boost: %s RPM", formatSignedRpm(snapshot.shooterBoost())));
            }

            if (shooterRpmField != null) shooterRpmField.setText(snapshot.shooterRpmFieldText());
            if (shooterRpmSlider != null) shooterRpmSlider.setValue(snapshot.shooterRpmSliderValue());
            if (shooterRpmSliderValueLabel != null) shooterRpmSliderValueLabel.setText(snapshot.shooterRpmSliderText());
            if (hoodSetpointField != null) hoodSetpointField.setText(snapshot.hoodFieldText());
            if (hoodSetpointSlider != null) hoodSetpointSlider.setValue(snapshot.hoodSliderValue());
            if (hoodSliderValueLabel != null) hoodSliderValueLabel.setText(snapshot.hoodSliderText());
            if (shooterRpmStatusLabel != null) shooterRpmStatusLabel.setText(snapshot.shooterRpmStatus());
            if (hoodSetpointStatusLabel != null) hoodSetpointStatusLabel.setText(snapshot.hoodStatus());
            if (shooterControlErrorLabel != null) shooterControlErrorLabel.setText(snapshot.shooterControlError());

            if (funnlingTile != null) {
                animateIfStateChanged(funnlingTile, snapshot.funnling());
                funnlingTile.setSelected(snapshot.funnling());
                updateToggleTileStyle(funnlingTile, snapshot.funnling());
            }
            if (manualTile != null) {
                animateIfStateChanged(manualTile, snapshot.manual());
                manualTile.setSelected(snapshot.manual());
                updateToggleTileStyle(manualTile, snapshot.manual());
            }
            if (passingHumanPlayerTile != null) {
                animateIfStateChanged(passingHumanPlayerTile, snapshot.passingHumanPlayer());
                passingHumanPlayerTile.setSelected(snapshot.passingHumanPlayer());
                updateToggleTileStyle(passingHumanPlayerTile, snapshot.passingHumanPlayer());
            }
            if (passingDepotTile != null) {
                animateIfStateChanged(passingDepotTile, snapshot.passingDepot());
                passingDepotTile.setSelected(snapshot.passingDepot());
                updateToggleTileStyle(passingDepotTile, snapshot.passingDepot());
            }

            renderFieldState(
                snapshot.alliance(),
                snapshot.poseX(),
                snapshot.poseY(),
                snapshot.poseDeg(),
                snapshot.gamePieces()
            );
            playReplayEvents(snapshot.replayEvents());
        } finally {
            suppressDashboardPublishing = false;
            updateReplayControls();
        }
    }

    private void markReplayEvent(String eventId) {
        pendingReplayEvents.add(eventId);
    }

    private String drainReplayEvents() {
        if (pendingReplayEvents.isEmpty()) {
            return "";
        }
        String value = String.join(",", pendingReplayEvents);
        pendingReplayEvents.clear();
        return value;
    }

    private void playReplayEvents(String replayEvents) {
        if (replayEvents == null || replayEvents.isBlank()) {
            return;
        }
        for (String eventId : replayEvents.split(",")) {
            ButtonBase button = getReplayEventButton(eventId.trim());
            if (button != null) {
                playClickAnimation(button);
            }
        }
    }

    private ButtonBase getReplayEventButton(String eventId) {
        return switch (eventId) {
            case "zeroGyro" -> zeroGyroTile;
            case "driveMode" -> driveModeTile;
            case "shoot" -> shootTile;
            case "intake" -> intakeTile;
            case "intakeIn" -> intakeInTile;
            case "spindexerReverse" -> spindexerReverseTile;
            case "hoodDown" -> hoodDownTile;
            case "confirm" -> confirmTile;
            case "boostDown" -> shooterBoostDownButton;
            case "boostUp" -> shooterBoostUpButton;
            case "sendSetpoints" -> shooterSendSetpointsButton;
            case "spindexerStart" -> spindexerStartButton;
            case "spindexerControlReverse" -> spindexerControlReverseButton;
            case "spindexerStop" -> spindexerStopButton;
            default -> null;
        };
    }

    private void animateIfStateChanged(ToggleButton button, boolean nextValue) {
        if (button.isSelected() != nextValue) {
            playClickAnimation(button);
        }
    }

    private String replayEventIdFor(String titleText) {
        return switch (titleText) {
            case "Zero Gyro", "ZeroGyro pressed (200ms pulse)" -> "zeroGyro";
            case "Driving", "DriveMode set true (200ms pulse)" -> "driveMode";
            case "Shoot", "Shoot pressed (200ms pulse)" -> "shoot";
            case "Intake", "Intake pressed (200ms pulse)" -> "intake";
            case "Intake In", "IntakeIn pressed (200ms pulse)" -> "intakeIn";
            case "Spindexer Reverse", "Spindexer reverse pressed (200ms pulse)" -> "spindexerReverse";
            case "Hood Down", "Hood down pressed (200ms pulse)" -> "hoodDown";
            case "Confirm", "Confirm pressed (200ms pulse)" -> "confirm";
            case "1771 passing/Human Player" -> "passingHumanPlayer";
            case "1771 passing/Depot" -> "passingDepot";
            default -> null;
        };
    }

    private void applyCanShootText(String text) {
        canShootLabel.setText(text);
        String color = "#94a3b8";
        if ("CAN SHOOT".equalsIgnoreCase(text)) {
            color = "#22c55e";
        } else if ("CANNOT SHOOT".equalsIgnoreCase(text)) {
            color = "#ef4444";
        }
        canShootLabel.setStyle(String.format("""
            -fx-text-fill: %s;
            -fx-font-weight: 800;
            -fx-font-size: 12;
        """, color));
    }

    private void applyShooterStatusText(String text) {
        shooterStatusLabel.setText(text);
        String color = "AT SPEED".equalsIgnoreCase(text) ? "#22c55e" : "#ef4444";
        shooterStatusLabel.setStyle(String.format("""
            -fx-text-fill: %s;
            -fx-font-weight: 800;
            -fx-font-size: 16;
        """, color));
    }

    private double[] readCurrentPose() {
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

        return new double[] {poseX, poseY, poseDeg};
    }

    private StackPane buildMatchTimerTile() {
        setMatchTimerColor("#f8fafc");

        Label title = new Label("Match Timer");
        title.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700;");

        canShootLabel.setStyle("""
            -fx-text-fill: #94a3b8;
            -fx-font-weight: 800;
            -fx-font-size: 12;
        """);

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

        VBox content = new VBox(6, title, matchTimerLabel, canShootLabel, hubStatusLabel, hubAllianceLabel, hubCountdownLabel);
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
            String replayEventId = replayEventIdFor(titleText);
            if (replayEventId != null) {
                markReplayEvent(replayEventId);
            }
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

    private ToggleButton buildToggleActionTile(
        String titleText,
        BooleanPublisher publisher,
        String logMessage
    ) {
        ToggleButton tile = new ToggleButton(titleText);
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setMaxHeight(Double.MAX_VALUE);
        tile.setPadding(new Insets(10));
        tile.setCursor(Cursor.HAND);
        tile.setSelected(false);
        updateToggleTileStyle(tile, false);
        publisher.set(false);

        tile.setOnAction(e -> {
            playClickAnimation(tile);
            boolean selected = tile.isSelected();
            publisher.set(selected);
            updateToggleTileStyle(tile, selected);
            appendLog(logMessage + (selected ? " true" : " false"));
        });

        return tile;
    }

    private void updateToggleTileStyle(ToggleButton tile, boolean selected) {
        if (selected) {
            tile.setStyle("""
                -fx-background-color: #16a34a;
                -fx-background-radius: 16;
                -fx-border-color: #22c55e;
                -fx-border-radius: 16;
                -fx-text-fill: #f8fafc;
                -fx-font-weight: 800;
                -fx-font-size: 16;
            """);
        } else {
            tile.setStyle("""
                -fx-background-color: #0f172a;
                -fx-background-radius: 16;
                -fx-border-color: #1f2a44;
                -fx-border-radius: 16;
                -fx-text-fill: #f8fafc;
                -fx-font-weight: 700;
                -fx-font-size: 16;
            """);
        }
    }

    private void playClickAnimation(ButtonBase tile) {
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
        ShootWindow shootWindow = getShootWindow(seconds);
        updateMatchTimerStyle(shootWindow);
        updateCanShootLabel(shootWindow);
    }

    private void updateHubTimer() {
        if (matchTimeSub == null || allianceSub == null) return;
        double remaining = matchTimeSub.get();
        if (remaining <= 0) return;
        ShootWindow shootWindow = getShootWindow(remaining);

        double elapsed = MATCH_SECONDS - remaining;
        if (elapsed < 0) elapsed = 0;

        String alliance = allianceSub.get().trim();
        boolean isBlue = alliance.equalsIgnoreCase("B");
        boolean isRed = alliance.equalsIgnoreCase("R");

        if (elapsed < AUTO_SECONDS) {
            setHubStatus("BOTH ACTIVE", "--", "--", getPreShootColor(shootWindow, "#94a3b8"));
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
                getPreShootColor(shootWindow, "#94a3b8")
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
                setHubStatus(
                    "HUB: ?",
                    "--",
                    formatSecondsFloor(shiftTimeLeft),
                    getPreShootColor(shootWindow, "#94a3b8")
                );
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
            setHubStatus(
                status,
                activeTeam,
                formatSecondsFloor(shiftTimeLeft),
                getPreShootColor(shootWindow, color)
            );
            return;
        }

        double endgameElapsed = shiftsElapsed - shiftsTotal;
        if (endgameElapsed < ENDGAME_SECONDS) {
            setHubStatus(
                "BOTH ACTIVE",
                "BOTH ACTIVE",
                formatSecondsFloor(ENDGAME_SECONDS - endgameElapsed),
                getPreShootColor(shootWindow, "#94a3b8")
            );
            return;
        }

        setHubStatus("BOTH ACTIVE", "BOTH ACTIVE", "--", getPreShootColor(shootWindow, "#94a3b8"));
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

    private static final class ShootWindow {
        private final boolean known;
        private final boolean canShoot;
        private final double secondsUntilCanShoot;

        private ShootWindow(boolean known, boolean canShoot, double secondsUntilCanShoot) {
            this.known = known;
            this.canShoot = canShoot;
            this.secondsUntilCanShoot = secondsUntilCanShoot;
        }
    }

    private ShootWindow getShootWindow(double remaining) {
        if (remaining <= 0) {
            return new ShootWindow(true, false, Double.POSITIVE_INFINITY);
        }
        if (allianceSub == null) {
            return new ShootWindow(false, false, Double.POSITIVE_INFINITY);
        }

        String alliance = allianceSub.get().trim();
        boolean isBlue = alliance.equalsIgnoreCase("Blue");
        boolean isRed = alliance.equalsIgnoreCase("Red");
        if (!isBlue && !isRed) {
            return new ShootWindow(false, false, Double.POSITIVE_INFINITY);
        }

        double elapsed = MATCH_SECONDS - remaining;
        if (elapsed < 0) elapsed = 0;

        if (elapsed < AUTO_SECONDS) {
            return new ShootWindow(true, true, 0.0);
        }

        double teleopRemaining = remaining;
        if (teleopRemaining > TELEOP_SECONDS) teleopRemaining = TELEOP_SECONDS;
        if (teleopRemaining < 0) teleopRemaining = 0;
        double teleopElapsed = TELEOP_SECONDS - teleopRemaining;
        if (teleopElapsed < TRANSITION_SECONDS) {
            return new ShootWindow(true, true, 0.0);
        }

        double shiftsElapsed = teleopElapsed - TRANSITION_SECONDS;
        double shiftsTotal = SHIFT_SECONDS * SHIFT_COUNT;
        if (shiftsElapsed < shiftsTotal) {
            int shiftIndex = (int) Math.floor(shiftsElapsed / SHIFT_SECONDS);
            double shiftTimeLeft = SHIFT_SECONDS - (shiftsElapsed % SHIFT_SECONDS);

            String winner = autoWinnerSub != null ? autoWinnerSub.get().trim() : "";
            boolean winnerBlue = winner.equalsIgnoreCase("Blue");
            boolean winnerRed = winner.equalsIgnoreCase("Red");

            if (!(winnerBlue || winnerRed)) {
                return new ShootWindow(false, false, Double.POSITIVE_INFINITY);
            }

            boolean shift1ActiveBlue = winnerRed;
            boolean activeBlue = (shiftIndex % 2 == 0) ? shift1ActiveBlue : !shift1ActiveBlue;
            boolean isActive = isBlue ? activeBlue : !activeBlue;
            if (isActive) {
                return new ShootWindow(true, true, 0.0);
            }
            return new ShootWindow(true, false, shiftTimeLeft);
        }

        double endgameElapsed = shiftsElapsed - shiftsTotal;
        if (endgameElapsed < ENDGAME_SECONDS) {
            return new ShootWindow(true, true, 0.0);
        }

        return new ShootWindow(true, true, 0.0);
    }

    private void setMatchTimerColor(String color) {
        matchTimerLabel.setStyle(String.format("""
            -fx-text-fill: %s;
            -fx-font-weight: 900;
            -fx-font-size: 36;
        """, color));
    }

    private void updateMatchTimerStyle(ShootWindow shootWindow) {
        String color = "#f8fafc";
        setMatchTimerColor(getPreShootColor(shootWindow, color));
    }

    private void updateCanShootLabel(ShootWindow shootWindow) {
        if (!shootWindow.known) {
            canShootLabel.setText("CAN SHOOT: --");
            canShootLabel.setStyle("""
                -fx-text-fill: #94a3b8;
                -fx-font-weight: 800;
                -fx-font-size: 12;
            """);
            return;
        }

        boolean canShoot = shootWindow.canShoot;
        canShootLabel.setText(canShoot ? "CAN SHOOT" : "CANNOT SHOOT");
        canShootLabel.setStyle(String.format("""
            -fx-text-fill: %s;
            -fx-font-weight: 800;
            -fx-font-size: 12;
        """, canShoot ? "#22c55e" : "#ef4444"));
    }

    private String getPreShootColor(ShootWindow shootWindow, String fallback) {
        if (shootWindow.known && !shootWindow.canShoot && shootWindow.secondsUntilCanShoot > 0.0) {
            if (shootWindow.secondsUntilCanShoot <= 10.0) {
                return "#22c55e";
            }
            if (shootWindow.secondsUntilCanShoot <= 15.0) {
                boolean flashOn = (System.currentTimeMillis() / 300) % 2 == 0;
                return flashOn ? "#22c55e" : fallback;
            }
        }
        return fallback;
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

    private StackPane buildMotorHealthTile() {
        Label title = new Label("Motor Health");
        title.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700;");
        motorHealthSummaryLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: 800; -fx-font-size: 18;");
        motorDisconnectedLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 13;");
        motorHotLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 13;");

        Label thresholdLabel = new Label(String.format("Hot threshold: %.4f C", MOTOR_TOO_HOT_THRESHOLD_C));
        thresholdLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12;");

        VBox content = new VBox(8, title, motorHealthSummaryLabel, motorDisconnectedLabel, motorHotLabel, thresholdLabel);
        content.setAlignment(Pos.CENTER_LEFT);

        StackPane tile = new StackPane(content);
        tile.setPadding(new Insets(10));
        tile.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 16;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 16;
        """);
        tile.setCursor(Cursor.HAND);
        tile.setOnMouseClicked(e -> showHealthDetails("Motor Health Details", latestMotorHealthIssues));
        return tile;
    }

    private StackPane buildRoboRioHealthTile() {
        Label title = new Label("roboRIO Health");
        title.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700;");
        roboRioHealthSummaryLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: 800; -fx-font-size: 18;");
        roboRioRestartLabel.setWrapText(true);
        roboRioPowerLabel.setWrapText(true);
        roboRioRailsLabel.setWrapText(true);
        roboRioRestartLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 12;");
        roboRioPowerLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 12;");
        roboRioRailsLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 12;");

        VBox content = new VBox(8, title, roboRioHealthSummaryLabel, roboRioRestartLabel, roboRioPowerLabel, roboRioRailsLabel);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setFillWidth(true);

        StackPane tile = new StackPane(content);
        tile.setPadding(new Insets(10));
        tile.setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 16;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 16;
        """);
        tile.setCursor(Cursor.HAND);
        tile.setOnMouseClicked(e -> showHealthDetails("roboRIO Health Details", latestRoboRioIssues));
        return tile;
    }

    private void updateMotorHealth() {
        tooHotThresholdPub.set(MOTOR_TOO_HOT_THRESHOLD_C);

        List<String> disconnectedMotors = new ArrayList<>();
        List<String> hotMotors = new ArrayList<>();
        latestMotorHealthIssues.clear();
        long nowMicros = WPIUtilJNI.now();
        boolean sawAnyMotorSubtable = false;

        for (String motorName : motorHealthTable.getSubTables()) {
            sawAnyMotorSubtable = true;
            NetworkTable motorTable = motorHealthTable.getSubTable(motorName);
            String displayName = motorTable.getEntry("Name").getString(motorName);
            var connectedEntry = motorTable.getEntry("Connected");
            var tempEntry = motorTable.getEntry("TempC");
            boolean connected = connectedEntry.getBoolean(false);
            long connectedAgeMicros = connectedEntry.getLastChange() == 0 ? Long.MAX_VALUE : (nowMicros - connectedEntry.getLastChange());
            long tempAgeMicros = tempEntry.getLastChange() == 0 ? Long.MAX_VALUE : (nowMicros - tempEntry.getLastChange());
            boolean stale = connectedAgeMicros > MOTOR_HEALTH_STALE_MICROS || tempAgeMicros > MOTOR_HEALTH_STALE_MICROS;
            if (stale) {
                connected = false;
            }
            double tempC = tempEntry.getDouble(0.0);
            boolean tooHot = tempC > MOTOR_TOO_HOT_THRESHOLD_C;

            if (!connected) {
                disconnectedMotors.add(displayName);
                latestMotorHealthIssues.add(stale
                    ? displayName + ": no recent NetworkTables updates"
                    : displayName + ": Connected=false");
            }
            if (tooHot) {
                hotMotors.add(String.format("%s (%.1f C)", displayName, tempC));
                latestMotorHealthIssues.add(String.format("%s: temp %.1f C exceeds %.4f C", displayName, tempC, MOTOR_TOO_HOT_THRESHOLD_C));
            }

            motorTable.getEntry("TooHot").setBoolean(tooHot);
        }

        String[] publishedDisconnected = motorHealthTable.getEntry("DisconnectedMotorsList").getStringArray(new String[0]);
        if (publishedDisconnected.length == 0) {
            publishedDisconnected = motorHealthTable.getEntry("DisconnectedMotors").getStringArray(new String[0]);
        }
        String[] publishedHot = motorHealthTable.getEntry("HotMotors").getStringArray(new String[0]);
        boolean hasPublishedDisconnected = publishedDisconnected.length > 0;
        boolean hasPublishedHot = publishedHot.length > 0;
        boolean publishedAllConnected = motorHealthTable.getEntry("AllConnected").getBoolean(disconnectedMotors.isEmpty());
        boolean publishedAllCool = motorHealthTable.getEntry("AllCool").getBoolean(hotMotors.isEmpty());
        boolean publishedHealthy = motorHealthTable.getEntry("Healthy").getBoolean(disconnectedMotors.isEmpty() && hotMotors.isEmpty());
        String publishedOverallStatus = motorHealthTable.getEntry("OverallStatus").getString("");

        boolean allConnected;
        boolean allCool;
        boolean healthy;

        if (!sawAnyMotorSubtable || !publishedOverallStatus.isBlank() || hasPublishedDisconnected || hasPublishedHot) {
            disconnectedMotors = new ArrayList<>(Arrays.asList(publishedDisconnected));
            hotMotors = new ArrayList<>(Arrays.asList(publishedHot));
            allConnected = publishedAllConnected;
            allCool = publishedAllCool;
            healthy = publishedHealthy;
            latestMotorHealthIssues.clear();
            if (!allConnected) {
                if (disconnectedMotors.isEmpty()) {
                    latestMotorHealthIssues.add("AllConnected is false, but DisconnectedMotors is empty.");
                } else {
                    for (String name : disconnectedMotors) {
                        latestMotorHealthIssues.add(name + ": reported disconnected");
                    }
                }
            }
            if (!allCool) {
                if (hotMotors.isEmpty()) {
                    latestMotorHealthIssues.add("AllCool is false, but HotMotors is empty.");
                } else {
                    for (String name : hotMotors) {
                        latestMotorHealthIssues.add(name + ": reported hot");
                    }
                }
            }
            if (healthy) {
                latestMotorHealthIssues.add("AllConnected and AllCool are true.");
            }
        } else {
            allConnected = disconnectedMotors.isEmpty();
            allCool = hotMotors.isEmpty();
            healthy = allConnected && allCool;
        }

        allConnectedPub.set(allConnected);
        allCoolPub.set(allCool);
        healthyPub.set(healthy);
        disconnectedMotorsPub.set(disconnectedMotors.toArray(String[]::new));
        hotMotorsPub.set(hotMotors.toArray(String[]::new));

        String status;
        if (healthy) {
            status = "HEALTHY";
            if (latestMotorHealthIssues.isEmpty()) {
                latestMotorHealthIssues.add("All motors connected and below the hot threshold.");
            }
        } else if (!allConnected && !allCool) {
            status = "DISCONNECTED + HOT";
        } else if (!allConnected) {
            status = "DISCONNECTED";
        } else {
            status = "TOO HOT";
        }
        overallStatusPub.set(status);

        motorHealthSummaryLabel.setText(status);
        motorHealthSummaryLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-weight: 800; -fx-font-size: 18;",
            healthy ? "#22c55e" : "#ef4444"
        ));
        motorDisconnectedLabel.setText(
            disconnectedMotors.isEmpty()
                ? "Disconnected: none"
                : "Disconnected: " + String.join(", ", disconnectedMotors)
        );
        motorHotLabel.setText(
            hotMotors.isEmpty()
                ? "Hot motors: none"
                : "Hot motors: " + String.join(", ", hotMotors)
        );
    }

    private void updateRoboRioHealth() {
        boolean rebootedRecently = roboRioHealthTable.getEntry("RebootedRecently").getBoolean(false);
        boolean brownedOut = roboRioHealthTable.getEntry("BrownedOut").getBoolean(false);
        double batteryVoltage = roboRioHealthTable.getEntry("BatteryVoltage").getDouble(0.0);
        double inputVoltage = roboRioHealthTable.getEntry("InputVoltage").getDouble(0.0);
        double inputCurrent = roboRioHealthTable.getEntry("InputCurrent").getDouble(0.0);
        double brownoutVoltage = roboRioHealthTable.getEntry("BrownoutVoltage").getDouble(0.0);
        boolean systemActive = roboRioHealthTable.getEntry("SystemActive").getBoolean(true);
        double commsDisableCount = roboRioHealthTable.getEntry("CommsDisableCount").getDouble(0.0);
        boolean enabled3V3 = roboRioHealthTable.getEntry("Enabled3V3").getBoolean(true);
        boolean enabled5V = roboRioHealthTable.getEntry("Enabled5V").getBoolean(true);
        boolean enabled6V = roboRioHealthTable.getEntry("Enabled6V").getBoolean(true);
        double faultCount3V3 = roboRioHealthTable.getEntry("FaultCount3V3").getDouble(0.0);
        double faultCount5V = roboRioHealthTable.getEntry("FaultCount5V").getDouble(0.0);
        double faultCount6V = roboRioHealthTable.getEntry("FaultCount6V").getDouble(0.0);
        double cpuTempC = roboRioHealthTable.getEntry("CPUTempC").getDouble(0.0);

        List<String> issues = new ArrayList<>();
        latestRoboRioIssues.clear();
        if (rebootedRecently) {
            issues.add("RIO rebooted recently");
            latestRoboRioIssues.add("RebootedRecently is true: the roboRIO likely restarted.");
        }
        if (brownedOut) {
            issues.add("Brownout detected");
            latestRoboRioIssues.add("BrownedOut is true: the roboRIO hit a low-voltage brownout.");
        }
        if (!systemActive) {
            issues.add("System inactive");
            latestRoboRioIssues.add("SystemActive is false: the roboRIO reports the system is not active.");
        }
        if (!enabled3V3) {
            issues.add("3.3V rail disabled");
            latestRoboRioIssues.add("Enabled3V3 is false: the 3.3V rail is disabled.");
        }
        if (!enabled5V) {
            issues.add("5V rail disabled");
            latestRoboRioIssues.add("Enabled5V is false: the 5V rail is disabled.");
        }
        if (!enabled6V) {
            issues.add("6V rail disabled");
            latestRoboRioIssues.add("Enabled6V is false: the 6V rail is disabled.");
        }
        if (faultCount3V3 > 0.0) {
            issues.add(String.format("3.3V rail faults %.0f", faultCount3V3));
            latestRoboRioIssues.add(String.format("FaultCount3V3 is %.0f.", faultCount3V3));
        }
        if (faultCount5V > 0.0) {
            issues.add(String.format("5V rail faults %.0f", faultCount5V));
            latestRoboRioIssues.add(String.format("FaultCount5V is %.0f.", faultCount5V));
        }
        if (faultCount6V > 0.0) {
            issues.add(String.format("6V rail faults %.0f", faultCount6V));
            latestRoboRioIssues.add(String.format("FaultCount6V is %.0f.", faultCount6V));
        }
        if (batteryVoltage > 0.0 && brownoutVoltage > 0.0 && batteryVoltage <= brownoutVoltage) {
            issues.add(String.format("Battery at brownout %.2fV", batteryVoltage));
            latestRoboRioIssues.add(String.format(
                "BatteryVoltage %.2f V is at or below BrownoutVoltage %.2f V.",
                batteryVoltage,
                brownoutVoltage
            ));
        }

        boolean healthy = issues.isEmpty();
        String status = healthy ? "HEALTHY" : String.join(" | ", issues);
        roboRioHealthyPub.set(healthy);
        roboRioStatusPub.set(status);
        if (healthy) {
            latestRoboRioIssues.add("No roboRIO health checks are currently failing.");
        }

        roboRioHealthSummaryLabel.setText(healthy ? "HEALTHY" : "CHECK RIO");
        roboRioHealthSummaryLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-weight: 800; -fx-font-size: 18;",
            healthy ? "#22c55e" : "#ef4444"
        ));
        roboRioRestartLabel.setText(String.format(
            "Rebooted Recently: %s\n" +
            "Browned Out: %s\n" +
            "System Active: %s\n" +
            "Comms Disable Count: %.0f",
            rebootedRecently, brownedOut, systemActive, commsDisableCount
        ));
        roboRioPowerLabel.setText(String.format(
            "Battery Voltage: %.2f V\n" +
            "Input Voltage: %.2f V\n" +
            "Input Current: %.1f A\n" +
            "Brownout Voltage: %.2f V\n" +
            "CPU Temp: %.1f C",
            batteryVoltage, inputVoltage, inputCurrent, brownoutVoltage, cpuTempC
        ));
        roboRioRailsLabel.setText(String.format(
            "3.3V Enabled: %s | Faults: %.0f\n" +
            "5V Enabled: %s | Faults: %.0f\n" +
            "6V Enabled: %s | Faults: %.0f",
            enabled3V3, faultCount3V3,
            enabled5V, faultCount5V,
            enabled6V, faultCount6V
        ));
    }

    private void showHealthDetails(String title, List<String> issues) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(String.join("\n", issues));
        alert.showAndWait();
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

        shooterBoostSlider = new Slider(-2000.0, 2000.0, 0.0);
        shooterBoostSlider.setShowTickMarks(true);
        shooterBoostSlider.setShowTickLabels(true);
        shooterBoostSlider.setMajorTickUnit(500.0);
        shooterBoostSlider.setMinorTickCount(4);
        shooterBoostSlider.setBlockIncrement(50.0);
        shooterBoostSlider.setPrefWidth(260);

        boostValueLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: 800;");
        shooterBoostSlider.valueProperty().addListener((obs, oldV, newV) -> {
            double boostRpm = newV.doubleValue();
            boostValueLabel.setText(String.format("Boost: %s RPM", formatSignedRpm(boostRpm)));
            if (!suppressDashboardPublishing) {
                shooterBoostCmd.set(boostRpm);
            }
        });
        shooterBoostCmd.set(shooterBoostSlider.getValue());

        shooterBoostDownButton = new Button("-200");
        shooterBoostUpButton = new Button("+200");
        styleBoostButton(shooterBoostDownButton, "#7f1d1d", "#ef4444", "#991b1b");
        styleBoostButton(shooterBoostUpButton, "#14532d", "#22c55e", "#166534");
        shooterBoostDownButton.setOnAction(e -> {
            playClickAnimation(shooterBoostDownButton);
            markReplayEvent("boostDown");
            adjustBoostBy(-200.0);
        });
        shooterBoostUpButton.setOnAction(e -> {
            playClickAnimation(shooterBoostUpButton);
            markReplayEvent("boostUp");
            adjustBoostBy(200.0);
        });

        HBox boostButtons = new HBox(10, shooterBoostDownButton, shooterBoostUpButton);
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

    private StackPane buildPassingTile() {
        Label title = new Label("1771 Glaze");
        title.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 800; -fx-font-size: 16;");

        passingHumanPlayerTile = buildToggleActionTile(
            "Human Player",
            passingHumanPlayerCmd,
            "1771 passing/Human Player toggled"
        );
        passingDepotTile = buildToggleActionTile(
            "Depot",
            passingDepotCmd,
            "1771 passing/Depot toggled"
        );
        passingHumanPlayerTile.setOnAction(e -> handlePassingToggle(
            passingHumanPlayerTile,
            passingHumanPlayerCmd,
            "1771 passing/Human Player",
            passingDepotTile,
            passingDepotCmd,
            "1771 passing/Depot"
        ));
        passingDepotTile.setOnAction(e -> handlePassingToggle(
            passingDepotTile,
            passingDepotCmd,
            "1771 passing/Depot",
            passingHumanPlayerTile,
            passingHumanPlayerCmd,
            "1771 passing/Human Player"
        ));

        passingHumanPlayerTile.setMinHeight(70);
        passingDepotTile.setMinHeight(70);
        passingHumanPlayerTile.setMaxWidth(Double.MAX_VALUE);
        passingDepotTile.setMaxWidth(Double.MAX_VALUE);

        HBox toggles = new HBox(10, passingHumanPlayerTile, passingDepotTile);
        toggles.setAlignment(Pos.CENTER);
        HBox.setHgrow(passingHumanPlayerTile, Priority.ALWAYS);
        HBox.setHgrow(passingDepotTile, Priority.ALWAYS);
        passingHumanPlayerTile.prefWidthProperty().bind(toggles.widthProperty().subtract(10).divide(2));
        passingDepotTile.prefWidthProperty().bind(toggles.widthProperty().subtract(10).divide(2));

        VBox content = new VBox(10, title, toggles);
        content.setAlignment(Pos.CENTER);
        content.setFillWidth(true);

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

    private void handlePassingToggle(
        ToggleButton currentButton,
        BooleanPublisher currentPublisher,
        String currentLabel,
        ToggleButton otherButton,
        BooleanPublisher otherPublisher,
        String otherLabel
    ) {
        playClickAnimation(currentButton);
        boolean selected = currentButton.isSelected();

        if (selected && otherButton.isSelected()) {
            otherButton.setSelected(false);
            otherPublisher.set(false);
            updateToggleTileStyle(otherButton, false);
            appendLog(otherLabel + " false");
        }

        String replayEventId = replayEventIdFor(currentLabel);
        if (replayEventId != null) {
            markReplayEvent(replayEventId);
        }
        currentPublisher.set(selected);
        updateToggleTileStyle(currentButton, selected);
        appendLog(currentLabel + (selected ? " true" : " false"));
    }

    private void adjustBoostBy(double delta) {
        if (shooterBoostSlider == null) return;
        double min = shooterBoostSlider.getMin();
        double max = shooterBoostSlider.getMax();
        double next = shooterBoostSlider.getValue() + delta;
        shooterBoostSlider.setValue(Math.max(min, Math.min(max, next)));
    }

    private String formatSignedRpm(double value) {
        return String.format("%+.0f", value);
    }

    private Parent buildShooterControlTab() {
        Label title = new Label("Shooter Controls");
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: 800; -fx-font-size: 18;");

        Label rpmLabel = new Label("Shooter RPM");
        rpmLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: 700;");
        shooterRpmField = new TextField();
        shooterRpmField.setPromptText("Type RPM (ex: 3200)");
        styleInputField(shooterRpmField);

        Label rpmSliderLabel = new Label("RPM Slider");
        rpmSliderLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700;");
        shooterRpmSlider = new Slider(0.0, 6000.0, 0.0);
        shooterRpmSlider.setShowTickMarks(true);
        shooterRpmSlider.setShowTickLabels(true);
        shooterRpmSlider.setMajorTickUnit(1000.0);
        shooterRpmSlider.setMinorTickCount(4);
        shooterRpmSlider.setBlockIncrement(50.0);
        shooterRpmSlider.setPrefWidth(260);
        shooterRpmSliderValueLabel = new Label("Slider: 0 RPM");
        shooterRpmSliderValueLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 600;");

        Label hoodLabel = new Label("Hood Setpoint (deg)");
        hoodLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: 700;");
        hoodSetpointField = new TextField();
        hoodSetpointField.setPromptText("Type angle (ex: 25)");
        styleInputField(hoodSetpointField);

        Label hoodSliderLabel = new Label("Hood Slider");
        hoodSliderLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700;");
        hoodSetpointSlider = new Slider(0.0, 90.0, 0.0);
        hoodSetpointSlider.setShowTickMarks(true);
        hoodSetpointSlider.setShowTickLabels(true);
        hoodSetpointSlider.setMajorTickUnit(15.0);
        hoodSetpointSlider.setMinorTickCount(2);
        hoodSetpointSlider.setBlockIncrement(1.0);
        hoodSetpointSlider.setPrefWidth(260);
        hoodSliderValueLabel = new Label("Slider: 0 deg");
        hoodSliderValueLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 600;");

        shooterRpmStatusLabel = new Label("Last sent: -- RPM");
        shooterRpmStatusLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 600;");
        hoodSetpointStatusLabel = new Label("Last sent: -- deg");
        hoodSetpointStatusLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 600;");

        shooterControlErrorLabel = new Label();
        shooterControlErrorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: 700;");

        shooterSendSetpointsButton = new Button("Send Setpoints");
        shooterSendSetpointsButton.setOnAction(e -> {
            playClickAnimation(shooterSendSetpointsButton);
            markReplayEvent("sendSetpoints");
            String rpmText = shooterRpmField.getText();
            boolean hasRpm = rpmText != null && !rpmText.isBlank();
            double rpm = Double.NaN;

            if (hasRpm) {
                rpm = parseDoubleOrWarn(rpmText, "RPM", shooterControlErrorLabel);
                if (Double.isNaN(rpm)) return;
            }

            double hoodDeg = parseDoubleOrWarn(hoodSetpointField.getText(), "Hood angle", shooterControlErrorLabel);
            if (Double.isNaN(hoodDeg)) return;

            if (hasRpm) {
                double boostRpm = shooterBoostSlider != null ? shooterBoostSlider.getValue() : 0.0;
                double boostedRpm = rpm + boostRpm;
                shooterTargetCmd.set(boostedRpm);
                shooterRpmStatusLabel.setText(String.format(
                    "Last sent: %.0f RPM (boost %s RPM -> %.0f RPM)",
                    rpm,
                    formatSignedRpm(boostRpm),
                    boostedRpm
                ));
                appendLog(String.format(
                    "Setpoints sent: %.0f RPM (boost %s RPM -> %.0f RPM), hood %.1f deg",
                    rpm,
                    formatSignedRpm(boostRpm),
                    boostedRpm,
                    hoodDeg
                ));
            } else {
                appendLog(String.format("Hood setpoint sent: %.1f deg", hoodDeg));
            }

            hoodSetpointCmd.set(hoodDeg);
            hoodSetpointStatusLabel.setText(String.format("Last sent: %.1f deg", hoodDeg));
            shooterControlErrorLabel.setText("");
        });
        shooterSendSetpointsButton.setPadding(new Insets(10, 18, 10, 18));
        shooterSendSetpointsButton.setCursor(Cursor.HAND);
        shooterSendSetpointsButton.setStyle("""
            -fx-background-color: #1d4ed8;
            -fx-background-radius: 10;
            -fx-text-fill: #f8fafc;
            -fx-font-weight: 800;
            -fx-font-size: 14;
        """);

        boolean[] updatingRpm = {false};
        shooterRpmSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (updatingRpm[0]) return;
            updatingRpm[0] = true;
            double value = newV.doubleValue();
            shooterRpmField.setText(String.format("%.0f", value));
            shooterRpmSliderValueLabel.setText(String.format("Slider: %.0f RPM", value));
            updatingRpm[0] = false;
        });
        shooterRpmField.textProperty().addListener((obs, oldV, newV) -> {
            if (updatingRpm[0]) return;
            if (newV == null || newV.isBlank()) {
                shooterRpmSliderValueLabel.setText("Slider: 0 RPM");
                return;
            }
            try {
                double value = Double.parseDouble(newV.trim());
                double clamped = Math.max(shooterRpmSlider.getMin(), Math.min(shooterRpmSlider.getMax(), value));
                updatingRpm[0] = true;
                shooterRpmSlider.setValue(clamped);
                shooterRpmSliderValueLabel.setText(String.format("Slider: %.0f RPM", clamped));
                updatingRpm[0] = false;
            } catch (NumberFormatException ignored) {
            }
        });

        boolean[] updatingHood = {false};
        hoodSetpointSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (updatingHood[0]) return;
            updatingHood[0] = true;
            double value = newV.doubleValue();
            hoodSetpointField.setText(String.format("%.1f", value));
            hoodSliderValueLabel.setText(String.format("Slider: %.1f deg", value));
            updatingHood[0] = false;
        });
        hoodSetpointField.textProperty().addListener((obs, oldV, newV) -> {
            if (updatingHood[0]) return;
            if (newV == null || newV.isBlank()) {
                hoodSliderValueLabel.setText("Slider: 0 deg");
                return;
            }
            try {
                double value = Double.parseDouble(newV.trim());
                double clamped = Math.max(hoodSetpointSlider.getMin(), Math.min(hoodSetpointSlider.getMax(), value));
                updatingHood[0] = true;
                hoodSetpointSlider.setValue(clamped);
                hoodSliderValueLabel.setText(String.format("Slider: %.1f deg", clamped));
                updatingHood[0] = false;
            } catch (NumberFormatException ignored) {
            }
        });

        shooterRpmField.setOnAction(e -> shooterSendSetpointsButton.fire());
        hoodSetpointField.setOnAction(e -> shooterSendSetpointsButton.fire());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(140);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);
        grid.add(rpmLabel, 0, 0);
        grid.add(shooterRpmField, 1, 0);
        grid.add(rpmSliderLabel, 0, 1);
        grid.add(new VBox(4, shooterRpmSlider, shooterRpmSliderValueLabel), 1, 1);
        grid.add(hoodLabel, 0, 2);
        grid.add(hoodSetpointField, 1, 2);
        grid.add(hoodSliderLabel, 0, 3);
        grid.add(new VBox(4, hoodSetpointSlider, hoodSliderValueLabel), 1, 3);

        spindexerStartButton = new Button("Start Spindexer");
        spindexerStartButton.setOnAction(e -> {
            playClickAnimation(spindexerStartButton);
            markReplayEvent("spindexerStart");
            spindexerReverseCmd.set(false);
            spindexerCmd.set(true);
            appendLog("Spindexer set true");
        });
        styleActionButton(spindexerStartButton);

        spindexerControlReverseButton = new Button("Reverse Spindexer");
        spindexerControlReverseButton.setOnAction(e -> {
            playClickAnimation(spindexerControlReverseButton);
            markReplayEvent("spindexerControlReverse");
            spindexerCmd.set(false);
            spindexerReverseCmd.set(true);
            appendLog("Spindexer reverse set true");
        });
        styleActionButton(spindexerControlReverseButton);

        spindexerStopButton = new Button("Stop Spindexer");
        spindexerStopButton.setOnAction(e -> {
            playClickAnimation(spindexerStopButton);
            markReplayEvent("spindexerStop");
            spindexerCmd.set(false);
            spindexerReverseCmd.set(false);
            appendLog("Spindexer set false");
        });
        styleActionButton(spindexerStopButton);

        HBox spindexerRow = new HBox(10, spindexerStartButton, spindexerControlReverseButton, spindexerStopButton);

        VBox card = new VBox(12, title, grid, shooterSendSetpointsButton, shooterControlErrorLabel, shooterRpmStatusLabel, hoodSetpointStatusLabel, spindexerRow);
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
        double[] pose = readCurrentPose();
        double[] gamePieces = gamePieceSub != null ? gamePieceSub.get() : new double[0];
        renderFieldState(alliance, pose[0], pose[1], pose[2], gamePieces);
    }

    private void renderFieldState(String alliance, double poseX, double poseY, double poseDeg, double[] gamePieces) {
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

        updateGamePieces(fieldW, fieldH, pixelsPerMeter, gamePieces);
    }

    private void updateGamePieces(double fieldW, double fieldH, double pixelsPerMeter, double[] arr) {
        if (gamePieceLayer == null) return;
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

