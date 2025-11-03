package ro.marcman.mixer.app;

import java.io.File;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone launcher that properly initializes JavaFX before starting the app
 * This works better with jpackage bundled executables
 */
public class StandaloneLauncher {
    
    public static void main(String[] args) {
        try {
            // Try to launch JavaFX application
            App.main(args);
        } catch (Exception e) {
            System.err.println("Error launching application: " + e.getMessage());
            e.printStackTrace();
            
            // Try alternative launch method
            try {
                System.out.println("Trying alternative launch method...");
                javafx.application.Application.launch(App.class, args);
            } catch (Exception e2) {
                System.err.println("Alternative launch also failed: " + e2.getMessage());
                e2.printStackTrace();
                System.exit(1);
            }
        }
    }
}


