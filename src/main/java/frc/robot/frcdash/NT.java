package frc.robot.frcdash;

import edu.wpi.first.networktables.*;

import java.util.function.Consumer;

public final class NT {
    private static final NetworkTableInstance inst = NetworkTableInstance.getDefault();

    private NT() {}

    public static void startClient(String server) {
        inst.stopClient();
        inst.setServer(server);
        inst.startClient4("FRC-Dashboard");
    }

    public static void stop() {
        inst.stopClient();
        inst.stopDSClient();
    }

    public static DoubleSubscriber subDouble(String topic, double defaultVal) {
        return inst.getDoubleTopic(topic).subscribe(defaultVal);
    }

    public static BooleanSubscriber subBool(String topic, boolean defaultVal) {
        return inst.getBooleanTopic(topic).subscribe(defaultVal);
    }

    public static DoubleArraySubscriber subDoubleArray(String topic, double[] defaultVal) {
        return inst.getDoubleArrayTopic(topic).subscribe(defaultVal);
    }

    public static StringSubscriber subString(String topic, String defaultVal) {
        return inst.getStringTopic(topic).subscribe(defaultVal);
    }

    public static StringArraySubscriber subStringArray(String topic, String[] defaultVal) {
        return inst.getStringArrayTopic(topic).subscribe(defaultVal);
    }

    public static BooleanPublisher pubBool(String topic) {
        return inst.getBooleanTopic(topic).publish();
    }

    public static StringPublisher pubString(String topic) {
        return inst.getStringTopic(topic).publish();
    }

    public static DoublePublisher pubDouble(String topic) {
        return inst.getDoubleTopic(topic).publish();
    }

    public static StringArrayPublisher pubStringArray(String topic) {
        return inst.getStringArrayTopic(topic).publish();
    }

    public static void onConnectionChange(Consumer<Boolean> cb) {
        inst.addConnectionListener(true, event -> {
            boolean connected = event.is(NetworkTableEvent.Kind.kConnected);
            cb.accept(connected);
        });
    }
}
