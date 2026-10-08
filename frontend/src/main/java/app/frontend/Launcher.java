package app.frontend;

import app.frontend.ui.MainApp;
import javafx.application.Application;

/** Plain Java entry point so packaged launchers load JavaFX from the bundled classpath. */
public final class Launcher {
    private Launcher() { }
    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
