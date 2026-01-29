# JavaFX Setup and Running Instructions

JavaFX has been successfully added to your project! Here are the ways to run the JavaFX test application:

## Method 1: Using Gradle (Recommended - Easiest)

Simply run:
```powershell
.\gradlew.bat runJavaFX
```

Or use the PowerShell script:
```powershell
.\runJavaFX.ps1
```

This method automatically handles all JavaFX module path configuration.

## Method 2: Running from IDE

If you want to run `JavaFXTest` directly from your IDE (VS Code, IntelliJ, Eclipse, etc.), you need to configure VM arguments.

### Get the VM Arguments

Run this command to get the exact VM arguments for your system:
```powershell
.\gradlew.bat printJavaFXPath
```

Look for the line that says `=== VM Arguments for IDE ===` and copy those arguments.

### Configure Your IDE

#### VS Code (Java Extension)
1. Open `.vscode/launch.json` (create if it doesn't exist)
2. Add a configuration like this:
```json
{
    "type": "java",
    "name": "Run JavaFX Test",
    "request": "launch",
    "mainClass": "frc.robot.JavaFXTest",
    "vmArgs": "--module-path \"C:/Users/rushi/.gradle/caches/modules-2/files-2.1/org.openjfx/javafx-fxml/17.0.2/5079f88d5345e988222c90a345ad682b7aaccad3/javafx-fxml-17.0.2-win.jar;C:/Users/rushi/.gradle/caches/modules-2/files-2.1/org.openjfx/javafx-controls/17.0.2/707f290bacde2738c0a7e1d0b4a8193002c29cf7/javafx-controls-17.0.2-win.jar;C:/Users/rushi/.gradle/caches/modules-2/files-2.1/org.openjfx/javafx-graphics/17.0.2/6f95886c8fed3e1b21370a199c3937846ef6b3cc/javafx-graphics-17.0.2-win.jar;C:/Users/rushi/.gradle/caches/modules-2/files-2.1/org.openjfx/javafx-base/17.0.2/1bd6dc88b180a6239a5067320c2f0d7f3526e1d2/javafx-base-17.0.2-win.jar\" --add-modules javafx.base,javafx.controls,javafx.fxml,javafx.graphics"
}
```

**Note:** Use the output from `printJavaFXPath` to get the correct paths for your system.

#### IntelliJ IDEA
1. Go to Run → Edit Configurations
2. Create a new "Application" configuration
3. Set Main class to: `frc.robot.JavaFXTest`
4. In VM options, paste the VM arguments from `printJavaFXPath`
5. Click OK and run

#### Eclipse
1. Right-click `JavaFXTest.java` → Run As → Run Configurations
2. Create a new Java Application configuration
3. Set Main class to: `frc.robot.JavaFXTest`
4. Go to Arguments tab → VM arguments
5. Paste the VM arguments from `printJavaFXPath`

## What Was Added

- **JavaFX Dependencies** (version 17.0.2):
  - `javafx-base` - Core functionality
  - `javafx-controls` - UI controls
  - `javafx-fxml` - FXML support
  - `javafx-graphics` - Rendering engine

- **Gradle Tasks**:
  - `runJavaFX` - Runs the test application
  - `printJavaFXPath` - Prints VM arguments for IDE configuration

## Troubleshooting

If you get "JavaFX runtime components are missing":
1. Make sure you've run `.\gradlew.bat build` at least once
2. Use Method 1 (Gradle) which handles everything automatically
3. If using IDE, verify the VM arguments are correct (run `printJavaFXPath` again)

## Next Steps

You can now:
- Import JavaFX classes: `import javafx.application.Application;`
- Create JavaFX applications in your project
- Use FXML for UI design
- Build rich desktop applications alongside your robot code

