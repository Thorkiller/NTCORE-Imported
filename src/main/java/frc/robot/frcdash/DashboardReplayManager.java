package frc.robot.frcdash;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class DashboardReplayManager {
    private static final String MAGIC = "FRC_DASH_REPLAY_V1";
    private static final DateTimeFormatter FILE_TS =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path replayDir;
    private final Supplier<DashboardSnapshot> snapshotSupplier;
    private final Consumer<DashboardSnapshot> snapshotConsumer;
    private final Consumer<String> logger;

    private Timeline recordTimeline;
    private BufferedWriter recordWriter;
    private Path currentRecordingPath;
    private long recordStartMillis;

    private List<DashboardSnapshot> loadedSnapshots = List.of();
    private Path loadedReplayPath;
    private boolean replaying;
    private int replayIndex;
    private long replayOffsetMillis;
    private long replayStartedAtMillis;
    private final AnimationTimer replayTimer;

    DashboardReplayManager(
        Path replayDir,
        Supplier<DashboardSnapshot> snapshotSupplier,
        Consumer<DashboardSnapshot> snapshotConsumer,
        Consumer<String> logger
    ) {
        this.replayDir = replayDir;
        this.snapshotSupplier = snapshotSupplier;
        this.snapshotConsumer = snapshotConsumer;
        this.logger = logger;
        this.replayTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tickReplay();
            }
        };
    }

    boolean isRecording() {
        return recordWriter != null;
    }

    boolean isReplaying() {
        return replaying;
    }

    Path getCurrentRecordingPath() {
        return currentRecordingPath;
    }

    Path getLoadedReplayPath() {
        return loadedReplayPath;
    }

    int getLoadedSnapshotCount() {
        return loadedSnapshots.size();
    }

    long getCurrentReplayElapsedMillis() {
        if (loadedSnapshots.isEmpty()) {
            return 0L;
        }
        if (replaying) {
            return Math.min(
                System.currentTimeMillis() - replayStartedAtMillis,
                getReplayDurationMillis()
            );
        }
        return replayOffsetMillis;
    }

    long getReplayDurationMillis() {
        if (loadedSnapshots.isEmpty()) {
            return 0L;
        }
        return loadedSnapshots.get(loadedSnapshots.size() - 1).elapsedMillis();
    }

    void startRecording() throws IOException {
        if (isRecording()) {
            return;
        }
        Files.createDirectories(replayDir);
        currentRecordingPath = replayDir.resolve(
            "dashboard-" + LocalDateTime.now().format(FILE_TS) + ".dashreplay"
        );
        recordWriter = Files.newBufferedWriter(currentRecordingPath, StandardCharsets.UTF_8);
        recordWriter.write(MAGIC);
        recordWriter.newLine();
        recordStartMillis = System.currentTimeMillis();

        writeSnapshot(false);

        recordTimeline = new Timeline(new KeyFrame(Duration.millis(100), e -> writeSnapshot(true)));
        recordTimeline.setCycleCount(Timeline.INDEFINITE);
        recordTimeline.play();
    }

    void stopRecording() {
        if (recordTimeline != null) {
            recordTimeline.stop();
            recordTimeline = null;
        }
        if (recordWriter != null) {
            try {
                recordWriter.flush();
                recordWriter.close();
            } catch (IOException ignored) {
            }
            recordWriter = null;
        }
    }

    Path getLatestReplayPath() throws IOException {
        if (!Files.isDirectory(replayDir)) {
            return null;
        }

        try (var paths = Files.list(replayDir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".dashreplay"))
                .max((left, right) -> {
                    try {
                        return Files.getLastModifiedTime(left).compareTo(Files.getLastModifiedTime(right));
                    } catch (IOException ex) {
                        return left.getFileName().toString().compareTo(right.getFileName().toString());
                    }
                })
                .orElse(null);
        }
    }

    void loadReplay(Path path) throws IOException {
        stopReplay();
        List<DashboardSnapshot> snapshots = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!MAGIC.equals(header)) {
                throw new IOException("Unsupported replay format");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                snapshots.add(DashboardSnapshot.deserialize(line));
            }
        }

        if (snapshots.isEmpty()) {
            throw new IOException("Replay file has no snapshots");
        }

        loadedSnapshots = snapshots;
        loadedReplayPath = path;
        replayIndex = 0;
        replayOffsetMillis = 0L;
        snapshotConsumer.accept(loadedSnapshots.get(0));
    }

    void playReplay() {
        if (loadedSnapshots.isEmpty()) {
            return;
        }
        replaying = true;
        replayStartedAtMillis = System.currentTimeMillis() - replayOffsetMillis;
        replayTimer.start();
    }

    void pauseReplay() {
        if (!replaying) {
            return;
        }
        replayOffsetMillis = System.currentTimeMillis() - replayStartedAtMillis;
        replaying = false;
        replayTimer.stop();
    }

    void stopReplay() {
        replaying = false;
        replayTimer.stop();
        replayIndex = 0;
        replayOffsetMillis = 0L;
    }

    void seekToElapsed(long elapsedMillis) {
        if (loadedSnapshots.isEmpty()) {
            return;
        }

        long clamped = Math.max(0L, Math.min(elapsedMillis, getReplayDurationMillis()));
        replayOffsetMillis = clamped;
        replayIndex = 0;
        while (replayIndex + 1 < loadedSnapshots.size()
            && loadedSnapshots.get(replayIndex + 1).elapsedMillis() <= clamped) {
            replayIndex++;
        }
        snapshotConsumer.accept(loadedSnapshots.get(replayIndex));
        if (replaying) {
            replayStartedAtMillis = System.currentTimeMillis() - replayOffsetMillis;
        }
    }

    private void tickReplay() {
        if (!replaying || loadedSnapshots.isEmpty()) {
            return;
        }

        long elapsed = System.currentTimeMillis() - replayStartedAtMillis;
        while (replayIndex + 1 < loadedSnapshots.size()
            && loadedSnapshots.get(replayIndex + 1).elapsedMillis() <= elapsed) {
            replayIndex++;
        }

        boolean finished = replayIndex >= loadedSnapshots.size() - 1;
        if (finished) {
            replayOffsetMillis = loadedSnapshots.get(replayIndex).elapsedMillis();
            replaying = false;
            replayTimer.stop();
        }

        snapshotConsumer.accept(loadedSnapshots.get(replayIndex));

        if (finished) {
            logger.accept("Replay finished: " + loadedReplayPath.getFileName());
        }
    }

    private void writeSnapshot(boolean flush) {
        if (recordWriter == null) {
            return;
        }

        try {
            DashboardSnapshot snapshot = snapshotSupplier.get()
                .withElapsedMillis(System.currentTimeMillis() - recordStartMillis);
            recordWriter.write(snapshot.serialize());
            recordWriter.newLine();
            if (flush) {
                recordWriter.flush();
            }
        } catch (IOException ex) {
            logger.accept("Replay recording error: " + ex.getMessage());
            stopRecording();
        }
    }

    record DashboardSnapshot(
        long elapsedMillis,
        boolean connected,
        String selectedTab,
        String batteryTopic,
        double batteryValue,
        String intakeMotorTempTopic,
        double intakeMotorTempValue,
        String intakeWheelsTempTopic,
        double intakeWheelsTempValue,
        String enabledTopic,
        String enabledValue,
        String visionTopic,
        boolean visionValue,
        double matchTime,
        String matchTimerText,
        String canShootText,
        String hubStatus,
        String hubAlliance,
        String hubCountdown,
        double shooterTarget,
        double shooterActual,
        String shooterStatus,
        double shooterBoost,
        String shooterRpmFieldText,
        double shooterRpmSliderValue,
        String shooterRpmSliderText,
        String hoodFieldText,
        double hoodSliderValue,
        String hoodSliderText,
        String shooterRpmStatus,
        String hoodStatus,
        String shooterControlError,
        String replayEvents,
        boolean funnling,
        boolean manual,
        boolean passingHumanPlayer,
        boolean passingDepot,
        String alliance,
        double poseX,
        double poseY,
        double poseDeg,
        double[] gamePieces
    ) {
        DashboardSnapshot withElapsedMillis(long newElapsedMillis) {
            return new DashboardSnapshot(
                newElapsedMillis,
                connected,
                selectedTab,
                batteryTopic,
                batteryValue,
                intakeMotorTempTopic,
                intakeMotorTempValue,
                intakeWheelsTempTopic,
                intakeWheelsTempValue,
                enabledTopic,
                enabledValue,
                visionTopic,
                visionValue,
                matchTime,
                matchTimerText,
                canShootText,
                hubStatus,
                hubAlliance,
                hubCountdown,
                shooterTarget,
                shooterActual,
                shooterStatus,
                shooterBoost,
                shooterRpmFieldText,
                shooterRpmSliderValue,
                shooterRpmSliderText,
                hoodFieldText,
                hoodSliderValue,
                hoodSliderText,
                shooterRpmStatus,
                hoodStatus,
                shooterControlError,
                replayEvents,
                funnling,
                manual,
                passingHumanPlayer,
                passingDepot,
                alliance,
                poseX,
                poseY,
                poseDeg,
                gamePieces != null ? Arrays.copyOf(gamePieces, gamePieces.length) : new double[0]
            );
        }

        String serialize() {
            return String.join("|",
                Long.toString(elapsedMillis),
                Boolean.toString(connected),
                encode(selectedTab),
                encode(batteryTopic),
                Double.toString(batteryValue),
                encode(intakeMotorTempTopic),
                Double.toString(intakeMotorTempValue),
                encode(intakeWheelsTempTopic),
                Double.toString(intakeWheelsTempValue),
                encode(enabledTopic),
                encode(enabledValue),
                encode(visionTopic),
                Boolean.toString(visionValue),
                Double.toString(matchTime),
                encode(matchTimerText),
                encode(canShootText),
                encode(hubStatus),
                encode(hubAlliance),
                encode(hubCountdown),
                Double.toString(shooterTarget),
                Double.toString(shooterActual),
                encode(shooterStatus),
                Double.toString(shooterBoost),
                encode(shooterRpmFieldText),
                Double.toString(shooterRpmSliderValue),
                encode(shooterRpmSliderText),
                encode(hoodFieldText),
                Double.toString(hoodSliderValue),
                encode(hoodSliderText),
                encode(shooterRpmStatus),
                encode(hoodStatus),
                encode(shooterControlError),
                encode(replayEvents),
                Boolean.toString(funnling),
                Boolean.toString(manual),
                Boolean.toString(passingHumanPlayer),
                Boolean.toString(passingDepot),
                encode(alliance),
                Double.toString(poseX),
                Double.toString(poseY),
                Double.toString(poseDeg),
                encodeDoubles(gamePieces)
            );
        }

        static DashboardSnapshot deserialize(String line) throws IOException {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 42) {
                throw new IOException("Malformed replay snapshot");
            }

            return new DashboardSnapshot(
                Long.parseLong(parts[0]),
                Boolean.parseBoolean(parts[1]),
                decode(parts[2]),
                decode(parts[3]),
                Double.parseDouble(parts[4]),
                decode(parts[5]),
                Double.parseDouble(parts[6]),
                decode(parts[7]),
                Double.parseDouble(parts[8]),
                decode(parts[9]),
                decode(parts[10]),
                decode(parts[11]),
                Boolean.parseBoolean(parts[12]),
                Double.parseDouble(parts[13]),
                decode(parts[14]),
                decode(parts[15]),
                decode(parts[16]),
                decode(parts[17]),
                decode(parts[18]),
                Double.parseDouble(parts[19]),
                Double.parseDouble(parts[20]),
                decode(parts[21]),
                Double.parseDouble(parts[22]),
                decode(parts[23]),
                Double.parseDouble(parts[24]),
                decode(parts[25]),
                decode(parts[26]),
                Double.parseDouble(parts[27]),
                decode(parts[28]),
                decode(parts[29]),
                decode(parts[30]),
                decode(parts[31]),
                decode(parts[32]),
                Boolean.parseBoolean(parts[33]),
                Boolean.parseBoolean(parts[34]),
                Boolean.parseBoolean(parts[35]),
                Boolean.parseBoolean(parts[36]),
                decode(parts[37]),
                Double.parseDouble(parts[38]),
                Double.parseDouble(parts[39]),
                Double.parseDouble(parts[40]),
                decodeDoubles(parts[41])
            );
        }

        private static String encode(String value) {
            String safe = value == null ? "" : value;
            return Base64.getUrlEncoder().encodeToString(safe.getBytes(StandardCharsets.UTF_8));
        }

        private static String decode(String value) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        }

        private static String encodeDoubles(double[] values) {
            if (values == null || values.length == 0) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(values[i]);
            }
            return encode(sb.toString());
        }

        private static double[] decodeDoubles(String value) {
            String decoded = decode(value);
            if (decoded.isBlank()) {
                return new double[0];
            }
            String[] parts = decoded.split(",");
            double[] out = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                out[i] = Double.parseDouble(parts[i]);
            }
            return out;
        }
    }
}
