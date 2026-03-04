package frc.robot.frcdash.ui;

import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringSubscriber;

import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Function;

public final class StringTopicTile extends VBox {
    private final NetworkTableInstance inst;

    private final Label titleLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final Label valueLabel = new Label();

    private String topicPath;
    private StringSubscriber sub;

    private final Function<String, String> formatter;
    private final String defaultValue;

    public StringTopicTile(
            NetworkTableInstance inst,
            String title,
            String subtitle,
            String initialTopicPath,
            String defaultValue,
            Function<String, String> formatter
    ) {
        this.inst = inst;
        this.formatter = formatter;
        this.defaultValue = defaultValue;

        titleLabel.setText(title);
        titleLabel.setStyle("-fx-text-fill: #f1f5f9; -fx-font-weight: 800; -fx-font-size: 16;");

        subtitleLabel.setText(subtitle);
        subtitleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12;");

        valueLabel.setStyle("-fx-text-fill: #e5e7eb; -fx-font-weight: 900; -fx-font-size: 28;");

        setPadding(new Insets(14));
        setMinHeight(140);
        setStyle("""
            -fx-background-color: #0f172a;
            -fx-background-radius: 16;
            -fx-border-color: #1f2a44;
            -fx-border-radius: 16;
        """);

        Region spacer = new Region();
        VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(titleLabel, subtitleLabel, spacer, valueLabel);

        setTopic(initialTopicPath);

        ContextMenu menu = buildContextMenu();
        addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
            menu.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    public void setTopic(String newTopicPath) {
        if (newTopicPath == null) return;
        newTopicPath = newTopicPath.trim();
        if (newTopicPath.isEmpty()) return;

        if (sub != null) sub.close();

        topicPath = newTopicPath;
        sub = inst.getStringTopic(topicPath).subscribe(defaultValue);

        subtitleLabel.setText("Topic: " + topicPath);
    }

    public void update() {
        if (sub == null) return;
        String v = sub.get();
        valueLabel.setText(formatter.apply(v));
    }

    public String getTopicPath() {
        return topicPath;
    }

    private ContextMenu buildContextMenu() {
        MenuItem setTopic = new MenuItem("Set topic...");
        setTopic.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog(getTopicPath());
            dialog.setTitle("Set Topic");
            dialog.setHeaderText("Enter a NetworkTables string topic path");
            dialog.setContentText("Topic:");

            dialog.showAndWait().ifPresent(this::setTopic);
        });

        MenuItem copyTopic = new MenuItem("Copy topic");
        copyTopic.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(getTopicPath());
            Clipboard.getSystemClipboard().setContent(cc);
        });

        return new ContextMenu(setTopic, copyTopic);
    }
}
