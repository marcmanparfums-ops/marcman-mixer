package ro.marcman.mixer.app;

/**
 * Simple launcher that ensures JavaFX is properly initialized
 * before starting the main application
 */
public class Launcher {
    public static void main(String[] args) {
        // Ensure JavaFX toolkit is initialized
        try {
            // This will initialize JavaFX if not already done
            javafx.application.Application.launch(App.class, args);
        } catch (IllegalStateException e) {
            // JavaFX already initialized, just start the app
            App.main(args);
        }
    }
}


