package frc.robot;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Simple JavaFX test application to verify JavaFX is properly configured.
 * Run this class directly to test JavaFX functionality.
 */
public class JavaFXTest extends Application {
    private int clickCount = 0;
    private Label statusLabel;

    @Override
    public void start(Stage primaryStage) {
        // Create UI elements
        Label titleLabel = new Label("JavaFX Test Application");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        statusLabel = new Label("JavaFX is working! Click the button to test.");
        statusLabel.setStyle("-fx-font-size: 14px;");

        Button testButton = new Button("Click Me!");
        testButton.setStyle("-fx-font-size: 16px; -fx-padding: 10px 20px;");
        testButton.setOnAction(e -> {
            clickCount++;
            statusLabel.setText("Button clicked " + clickCount + " time(s)! JavaFX is working correctly.");
        });

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-font-size: 14px;");
        closeButton.setOnAction(e -> Platform.exit());

        // Layout
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));
        root.getChildren().addAll(titleLabel, statusLabel, testButton, closeButton);

        // Create scene
        Scene scene = new Scene(root, 400, 300);

        // Setup stage
        primaryStage.setTitle("JavaFX Test - NTCORE");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(e -> Platform.exit());
        primaryStage.show();

        System.out.println("JavaFX application started successfully!");
    }

    /**
     * Main method to run the JavaFX test application.
     * This can be run independently from the robot code.
     */
    public static void main(String[] args) {
        System.out.println("Launching JavaFX test application...");
        launch(args);
    }
}

