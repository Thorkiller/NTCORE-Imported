package frc.robot.frcdash;


import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) {
        DashboardView view = new DashboardView();
        Scene scene = new Scene(view.root(), 1100, 700);

        stage.setTitle("FRC Dashboard");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
